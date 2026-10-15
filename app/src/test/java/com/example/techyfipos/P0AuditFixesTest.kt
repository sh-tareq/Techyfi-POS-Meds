package com.example.techyfipos

import com.example.techyfipos.data.AppPermissions
import com.example.techyfipos.data.AppRoles
import com.example.techyfipos.data.DataRepository
import com.example.techyfipos.data.PinVerifyResult
import com.example.techyfipos.data.SupervisorVerifyResult
import com.example.techyfipos.data.db.Product
import com.example.techyfipos.data.db.User
import com.example.techyfipos.ui.viewmodel.AuthViewModel
import com.example.techyfipos.ui.viewmodel.CartItem
import com.example.techyfipos.ui.viewmodel.CartViewModel
import com.example.techyfipos.ui.viewmodel.PriceMode
import com.example.techyfipos.utils.formatUnitsSold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import kotlinx.coroutines.flow.first
import org.junit.Test

class P0AuditFixesTest {

    class FakeSharedPreferences : android.content.SharedPreferences {
        private val map = mutableMapOf<String, Any?>()

        override fun getAll(): Map<String, *> = HashMap(map)
        override fun getString(key: String, defValue: String?): String? = map[key] as? String ?: defValue
        override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = (map[key] as? Set<*>)?.filterIsInstance<String>()?.toSet() ?: defValues
        override fun getInt(key: String, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long): Long = map[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = map[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun contains(key: String): Boolean = map.containsKey(key)
        override fun edit(): android.content.SharedPreferences.Editor = Editor()
        override fun registerOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}

        inner class Editor : android.content.SharedPreferences.Editor {
            private val temp = mutableMapOf<String, Any?>()
            private var clear = false

            override fun putString(key: String, value: String?): android.content.SharedPreferences.Editor { temp[key] = value; return this }
            override fun putStringSet(key: String, values: Set<String>?): android.content.SharedPreferences.Editor { temp[key] = values; return this }
            override fun putInt(key: String, value: Int): android.content.SharedPreferences.Editor { temp[key] = value; return this }
            override fun putLong(key: String, value: Long): android.content.SharedPreferences.Editor { temp[key] = value; return this }
            override fun putFloat(key: String, value: Float): android.content.SharedPreferences.Editor { temp[key] = value; return this }
            override fun putBoolean(key: String, value: Boolean): android.content.SharedPreferences.Editor { temp[key] = value; return this }
            override fun remove(key: String): android.content.SharedPreferences.Editor { temp[key] = this; return this }
            override fun clear(): android.content.SharedPreferences.Editor { clear = true; return this }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clear) map.clear()
                temp.forEach { (k, v) ->
                    if (v === this) map.remove(k) else map[k] = v
                }
                temp.clear()
                clear = false
            }
        }
    }

    class FakeUserDao(val users: MutableList<User> = mutableListOf()) : com.example.techyfipos.data.db.UserDao {
        override fun getAllUsers(): kotlinx.coroutines.flow.Flow<List<User>> = kotlinx.coroutines.flow.flowOf(users)
        override suspend fun getAllUsersOnce(): List<User> = ArrayList(users)
        override fun getActiveUsers(): kotlinx.coroutines.flow.Flow<List<User>> = kotlinx.coroutines.flow.flowOf(users.filter { it.isActive })
        override suspend fun getActiveUsersOnce(): List<User> = users.filter { it.isActive }
        override suspend fun getUserById(userId: Long): User? = users.firstOrNull { it.id == userId }
        override suspend fun getUserCount(): Int = users.size
        override suspend fun getOwnerCount(): Int = users.count { it.role == AppRoles.OWNER }
        override suspend fun getActiveOwnerCount(): Int = users.count { it.role == AppRoles.OWNER && it.isActive }
        override suspend fun getActiveSupervisors(): List<User> = users.filter { (it.role == AppRoles.OWNER || it.role == AppRoles.MANAGER) && it.isActive }
        override suspend fun insertUser(user: User): Long {
            val newId = (users.maxOfOrNull { it.id } ?: 0L) + 1L
            users.add(user.copy(id = newId))
            return newId
        }
        override suspend fun updatePinHash(userId: Long, pinHash: String) {
            val idx = users.indexOfFirst { it.id == userId }
            if (idx >= 0) users[idx] = users[idx].copy(pinHash = pinHash)
        }
        override suspend fun updatePermissions(userId: Long, customPermissions: String?) {
            val idx = users.indexOfFirst { it.id == userId }
            if (idx >= 0) users[idx] = users[idx].copy(customPermissions = customPermissions)
        }
        override suspend fun updateActiveStatus(userId: Long, isActive: Boolean) {
            val idx = users.indexOfFirst { it.id == userId }
            if (idx >= 0) users[idx] = users[idx].copy(isActive = isActive)
        }
        override suspend fun updateRole(userId: Long, role: String) {
            val idx = users.indexOfFirst { it.id == userId }
            if (idx >= 0) users[idx] = users[idx].copy(role = role)
        }
        override suspend fun updateUser(user: User) {
            val idx = users.indexOfFirst { it.id == user.id }
            if (idx >= 0) users[idx] = user
        }
        override suspend fun deleteUser(user: User) {
            users.removeAll { it.id == user.id }
        }
    }

    private fun hashSha256(str: String): String {
        val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(str.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun createRepositoryWithUsers(initialUsers: List<User>): Pair<DataRepository, Map<String, FakeSharedPreferences>> {
        val prefsMap = mutableMapOf<String, FakeSharedPreferences>()
        val mockContext = org.mockito.Mockito.mock(android.content.Context::class.java, org.mockito.Mockito.RETURNS_DEEP_STUBS)
        org.mockito.Mockito.`when`(mockContext.getSharedPreferences(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyInt())).thenAnswer { invocation ->
            val name = invocation.getArgument<String>(0)
            prefsMap.getOrPut(name) { FakeSharedPreferences() }
        }

        val fakeUserDao = FakeUserDao(initialUsers.toMutableList())
        val mockDatabase = org.mockito.Mockito.mock(com.example.techyfipos.data.db.AppDatabase::class.java, org.mockito.Mockito.RETURNS_DEEP_STUBS)
        org.mockito.Mockito.`when`(mockDatabase.userDao()).thenReturn(fakeUserDao)

        val mockSettingsDataStore = org.mockito.Mockito.mock(com.example.techyfipos.data.SettingsDataStore::class.java, org.mockito.Mockito.RETURNS_DEEP_STUBS)
        val repo = DataRepository(mockContext, mockDatabase, mockSettingsDataStore)
        return Pair(repo, prefsMap)
    }

    @Test
    fun testR21_01_idleActivityTracking() {
        val initialTime = 100_000L
        AuthViewModel.setLastUserActivityElapsedForTesting(initialTime)
        assertEquals(initialTime, AuthViewModel.getLastUserActivityElapsed())

        val before = android.os.SystemClock.elapsedRealtime()
        AuthViewModel.recordUserActivity()
        val after = android.os.SystemClock.elapsedRealtime()
        val recorded = AuthViewModel.getLastUserActivityElapsed()
        assertTrue("Recorded time $recorded should be within $before..$after", recorded in before..after)
    }

    @Test
    fun testR21_02_formatUnitsSoldWithDouble() {
        val wholeUnits = 15.0
        val fracUnits = 15.75
        val enWhole = formatUnitsSold(wholeUnits, isBangla = false)
        val enFrac = formatUnitsSold(fracUnits, isBangla = false)
        assertEquals("15", enWhole)
        assertEquals("15.75", enFrac)

        val bnWhole = formatUnitsSold(wholeUnits, isBangla = true)
        assertEquals("১৫", bnWhole)

        val enFormatString = "Invoices: %1\$d | Units: %2\$s"
        val formattedEn = String.format(java.util.Locale.US, enFormatString, 5, enWhole)
        assertEquals("Invoices: 5 | Units: 15", formattedEn)

        val bnFormatString = "চালান: %1\$d | পণ্য বিক্রয়: %2\$s"
        val formattedBn = String.format(java.util.Locale.US, bnFormatString, 5, bnWhole)
        assertEquals("চালান: 5 | পণ্য বিক্রয়: ১৫", formattedBn)
    }

    @Test
    fun testR21_03_cartHashParityAcrossPriceModes() {
        val product = Product(
            barcode = "ITEM-001",
            name = "Test Item",
            category = "General",
            purchasePrice = 80.0,
            sellingPrice = 100.0,
            stockQty = 10.0,
            reorderLevel = 2,
            unit = "pcs",
            wholesalePrice = 90.0,
            mrpPrice = 120.0
        )
        val cartItems = listOf(CartItem(product = product, quantity = 2.0))

        // Regular (Discounted) mode
        val hashRegular = CartViewModel.computeCartHash(cartItems, PriceMode.DISCOUNTED)
        val repoTriplesRegular = CartViewModel.buildSaleTriples(cartItems, PriceMode.DISCOUNTED)
        val repoHashRegular = CartViewModel.computeCartHashFromTriples(repoTriplesRegular)
        assertEquals(hashRegular, repoHashRegular)
        assertEquals("ITEM-001:2.0:100.0", hashRegular)

        // MRP mode
        val hashMrp = CartViewModel.computeCartHash(cartItems, PriceMode.MRP)
        val repoTriplesMrp = CartViewModel.buildSaleTriples(cartItems, PriceMode.MRP)
        val repoHashMrp = CartViewModel.computeCartHashFromTriples(repoTriplesMrp)
        assertEquals(hashMrp, repoHashMrp)
        assertEquals("ITEM-001:2.0:120.0", hashMrp)
        assertNotEquals(hashRegular, hashMrp)

        // Wholesale mode
        val hashWholesale = CartViewModel.computeCartHash(cartItems, PriceMode.WHOLESALE)
        val repoTriplesWholesale = CartViewModel.buildSaleTriples(cartItems, PriceMode.WHOLESALE)
        val repoHashWholesale = CartViewModel.computeCartHashFromTriples(repoTriplesWholesale)
        assertEquals(hashWholesale, repoHashWholesale)
        assertEquals("ITEM-001:2.0:90.0", hashWholesale)
        assertNotEquals(hashRegular, hashWholesale)
    }

    @Test
    fun testR22_04_supervisorApprovalInvalidAfterCartChange() {
        val product1 = Product(
            barcode = "ITEM-001",
            name = "Item 1",
            category = "Cat",
            purchasePrice = 50.0,
            sellingPrice = 100.0,
            mrpPrice = 150.0,
            stockQty = 10.0,
            reorderLevel = 1,
            unit = "pcs"
        )
        val product2 = Product(
            barcode = "ITEM-002",
            name = "Item 2",
            category = "Cat",
            purchasePrice = 60.0,
            sellingPrice = 120.0,
            stockQty = 10.0,
            reorderLevel = 1,
            unit = "pcs"
        )
        val initialCart = listOf(CartItem(product = product1, quantity = 1.0))
        val initialHash = CartViewModel.computeCartHash(initialCart, PriceMode.DISCOUNTED)

        // Adding an item modifies hash
        val modifiedCart = listOf(
            CartItem(product = product1, quantity = 1.0),
            CartItem(product = product2, quantity = 1.0)
        )
        val modifiedHash = CartViewModel.computeCartHash(modifiedCart, PriceMode.DISCOUNTED)
        assertNotEquals(initialHash, modifiedHash)

        // Changing quantity modifies hash
        val modifiedQtyCart = listOf(CartItem(product = product1, quantity = 2.0))
        val modifiedQtyHash = CartViewModel.computeCartHash(modifiedQtyCart, PriceMode.DISCOUNTED)
        assertNotEquals(initialHash, modifiedQtyHash)

        // Changing price mode modifies hash
        val modifiedModeHash = CartViewModel.computeCartHash(initialCart, PriceMode.MRP)
        assertNotEquals(initialHash, modifiedModeHash)
    }

    @Test
    fun testR22_01_cashierCannotBypassSupervisorLockoutWithOwnPin() = kotlinx.coroutines.test.runTest {
        val cashier = User(id = 1L, name = "Cashier", pinHash = hashSha256("1111"), role = AppRoles.CASHIER, maxDiscountPercent = 10.0, isActive = true)
        val owner = User(id = 2L, name = "Owner", pinHash = hashSha256("9999"), role = AppRoles.OWNER, maxDiscountPercent = 100.0, isActive = true)
        val (repo, _) = createRepositoryWithUsers(listOf(cashier, owner))

        // 3 wrong attempts (failures 1..3)
        for (i in 1..3) {
            val res = repo.verifySupervisorPinDetailed("0000")
            assertTrue("Expected Invalid", res is SupervisorVerifyResult.Invalid)
        }
        // 4th attempt: entering cashier's own PIN returns NotSupervisor without clearing the counter!
        val resOwn = repo.verifySupervisorPinDetailed("1111")
        assertEquals(SupervisorVerifyResult.NotSupervisor, resOwn)

        // 5th attempt: entering cashier's own PIN again (or wrong guess) triggers lockout!
        val res5th = repo.verifySupervisorPinDetailed("1111")
        assertTrue("5th failure must lock out", res5th is SupervisorVerifyResult.Locked)
    }

    @Test
    fun testR22_02_ownerPinResetAlertSurvivesLoginsUntilAcknowledgedAndChanged() = kotlinx.coroutines.test.runTest {
        val cashier = User(id = 1L, name = "Cashier", pinHash = hashSha256("1111"), role = AppRoles.CASHIER, maxDiscountPercent = 10.0, isActive = true)
        val owner = User(id = 2L, name = "Owner", pinHash = hashSha256("9999"), role = AppRoles.OWNER, maxDiscountPercent = 100.0, isActive = true)
        val (repo, _) = createRepositoryWithUsers(listOf(cashier, owner))

        // Reset owner PIN via device credentials
        val resetSuccess = repo.resetOwnerPinAfterDeviceCredential("2222")
        assertTrue(resetSuccess)
        assertTrue(repo.getUnacknowledgedOwnerPinResetTimestamp() > 0L)

        // Cashier logs in
        val cashierLogin = repo.verifyPinDetailed("1111")
        assertTrue(cashierLogin is PinVerifyResult.Success)
        repo.setSessionUser((cashierLogin as PinVerifyResult.Success).user)
        assertTrue("Alert must still be pending after cashier login", repo.getUnacknowledgedOwnerPinResetTimestamp() > 0L)

        // Owner logs in
        val ownerLogin = repo.verifyPinDetailed("2222")
        assertTrue(ownerLogin is PinVerifyResult.Success)
        repo.setSessionUser((ownerLogin as PinVerifyResult.Success).user)
        assertTrue("Alert must still be pending after owner login", repo.getUnacknowledgedOwnerPinResetTimestamp() > 0L)

        // Owner acknowledges alert
        repo.acknowledgeOwnerPinReset()
        assertTrue("Alert must still be pending until owner changes PIN", repo.getUnacknowledgedOwnerPinResetTimestamp() > 0L)

        // Owner updates PIN
        repo.updateStaff(userId = owner.id, newName = "Owner", newPin = "3333", oldPin = "2222")
        assertEquals("Alert cleared after acknowledgment AND pin change", 0L, repo.getUnacknowledgedOwnerPinResetTimestamp())

        // Permanent audit record remains in Settings
        val audit = repo.getOwnerPinResetAudit()
        assertNotNull(audit)
        assertTrue(audit!!.acknowledged)
        assertTrue(audit.pinChangedAfterReset)
    }

    @Test
    fun testR22_03_manageStaffCannotCreateHigherRoleOrGrantUnheldOrSystemPermissions() = kotlinx.coroutines.test.runTest {
        val cashier = User(
            id = 1L,
            name = "Cashier",
            pinHash = hashSha256("1111"),
            role = AppRoles.CASHIER,
            customPermissions = AppPermissions.serializePermissions(setOf(AppPermissions.SYSTEM_MANAGE_STAFF)),
            maxDiscountPercent = 10.0,
            isActive = true
        )
        val owner = User(id = 2L, name = "Owner", pinHash = hashSha256("9999"), role = AppRoles.OWNER, maxDiscountPercent = 100.0, isActive = true)
        val (repo, _) = createRepositoryWithUsers(listOf(cashier, owner))
        repo.setSessionUser(cashier)

        // Cannot create Manager
        assertThrows(DataRepository.UnauthorizedException::class.java) {
            kotlinx.coroutines.runBlocking {
                repo.createUser("Hacker Manager", "2222", AppRoles.MANAGER)
            }
        }

        // Cannot create Owner
        assertThrows(DataRepository.UnauthorizedException::class.java) {
            kotlinx.coroutines.runBlocking {
                repo.createUser("Hacker Owner", "2222", AppRoles.OWNER)
            }
        }

        // Cannot grant SYSTEM_PERMISSIONS
        assertThrows(DataRepository.UnauthorizedException::class.java) {
            kotlinx.coroutines.runBlocking {
                repo.createUser("Cashier 2", "2222", AppRoles.CASHIER, customPermissions = setOf(AppPermissions.SYSTEM_PERMISSIONS))
            }
        }

        // Cannot grant max discount higher than creator's own (10.0)
        assertThrows(DataRepository.UnauthorizedException::class.java) {
            kotlinx.coroutines.runBlocking {
                repo.createUser("Cashier 2", "2222", AppRoles.CASHIER, maxDiscount = 25.0)
            }
        }
    }

    @Test
    fun testR22_29_pinCollisionThrottlesWithLockoutAndGenericError() = kotlinx.coroutines.test.runTest {
        val owner = User(id = 1L, name = "Owner", pinHash = hashSha256("9999"), role = AppRoles.OWNER, maxDiscountPercent = 100.0, isActive = true)
        val cashier = User(
            id = 2L,
            name = "Cashier",
            pinHash = hashSha256("1111"),
            role = AppRoles.CASHIER,
            customPermissions = AppPermissions.serializePermissions(setOf(AppPermissions.SYSTEM_MANAGE_STAFF)),
            maxDiscountPercent = 10.0,
            isActive = true
        )
        val (repo, _) = createRepositoryWithUsers(listOf(owner, cashier))
        repo.setSessionUser(owner)

        // Try candidate PIN matching owner's PIN: should fail with generic message and record failed attempt
        for (i in 1..4) {
            val ex = assertThrows(IllegalStateException::class.java) {
                kotlinx.coroutines.runBlocking {
                    repo.createUser("Temp $i", "9999", AppRoles.CASHIER)
                }
            }
            assertEquals("Invalid or unavailable PIN", ex.message)
        }

        // 5th collision triggers lock
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                repo.createUser("Temp 5", "9999", AppRoles.CASHIER)
            }
        }

        assertTrue("Lockout must be active after 5 failed PIN attempts", repo.loginLockRemainingMillis() > 0L)

        // Next attempt must be blocked by lockout
        val lockedEx = assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                repo.createUser("Temp 6", "1234", AppRoles.CASHIER)
            }
        }
        assertTrue("Expected lockout message", lockedEx.message?.contains("locked out") == true)
    }

    @Test
    fun testR22_30_usersCannotRaiseOwnRoleDeactivateSelfOrGrantSystemPermissions() = kotlinx.coroutines.test.runTest {
        val cashier = User(
            id = 1L,
            name = "Cashier",
            pinHash = hashSha256("1111"),
            role = AppRoles.CASHIER,
            customPermissions = AppPermissions.serializePermissions(setOf(AppPermissions.SYSTEM_MANAGE_STAFF)),
            maxDiscountPercent = 10.0,
            isActive = true
        )
        val manager = User(
            id = 2L,
            name = "Manager",
            pinHash = hashSha256("2222"),
            role = AppRoles.MANAGER,
            customPermissions = AppPermissions.serializePermissions(setOf(AppPermissions.SYSTEM_MANAGE_STAFF, AppPermissions.SYSTEM_PERMISSIONS)),
            maxDiscountPercent = 50.0,
            isActive = true
        )
        val owner = User(id = 3L, name = "Owner", pinHash = hashSha256("9999"), role = AppRoles.OWNER, maxDiscountPercent = 100.0, isActive = true)
        val (repo, _) = createRepositoryWithUsers(listOf(cashier, manager, owner))

        // 1. Cashier attempts to raise own role
        repo.setSessionUser(cashier)
        assertThrows(DataRepository.UnauthorizedException::class.java) {
            kotlinx.coroutines.runBlocking {
                repo.updateStaff(userId = cashier.id, newName = "Cashier", newRole = AppRoles.MANAGER)
            }
        }

        // 2. Cashier attempts to deactivate self
        assertThrows(DataRepository.UnauthorizedException::class.java) {
            kotlinx.coroutines.runBlocking {
                repo.updateStaff(userId = cashier.id, newName = "Cashier", isActive = false)
            }
        }
        assertThrows(DataRepository.UnauthorizedException::class.java) {
            kotlinx.coroutines.runBlocking {
                repo.setStaffActive(cashier.id, false)
            }
        }

        // 3. Manager attempts to grant SYSTEM_BACKUP_RESTORE (non-owner cannot grant SYSTEM_* keys)
        repo.setSessionUser(manager)
        assertThrows(DataRepository.UnauthorizedException::class.java) {
            kotlinx.coroutines.runBlocking {
                repo.updateStaff(userId = cashier.id, newName = "Cashier", customPermissions = setOf(AppPermissions.SYSTEM_BACKUP_RESTORE))
            }
        }

        // 4. deleteUser reloads target from DB; passing forged object with role CASHIER for OWNER is rejected
        val forgedOwner = owner.copy(role = AppRoles.CASHIER)
        assertThrows(DataRepository.UnauthorizedException::class.java) {
            kotlinx.coroutines.runBlocking {
                repo.deleteUser(forgedOwner)
            }
        }
        // When owner tries to delete the last owner account, it is also rejected
        repo.setSessionUser(owner)
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                repo.deleteUser(owner)
            }
        }
    }

    @Test
    fun testMultiOwner_creationPromotionAndDemotion() = kotlinx.coroutines.test.runTest {
        val owner1 = User(id = 1L, name = "Owner 1", pinHash = hashSha256("1111"), role = AppRoles.OWNER, maxDiscountPercent = 100.0, isActive = true)
        val cashier = User(id = 2L, name = "Cashier", pinHash = hashSha256("2222"), role = AppRoles.CASHIER, maxDiscountPercent = 10.0, isActive = true)
        val (repo, _) = createRepositoryWithUsers(listOf(owner1, cashier))
        repo.setSessionUser(owner1)

        // 1. Existing Owner CAN create a second Owner account
        val owner2Id = repo.createUser("Owner 2", "3333", AppRoles.OWNER)
        assertTrue(owner2Id > 0L)
        val owner2 = repo.allUsers.first().find { it.id == owner2Id }
        assertNotNull(owner2)
        assertEquals(AppRoles.OWNER, owner2?.role)

        // 2. Existing Owner CAN promote Cashier to Owner
        repo.updateStaff(userId = cashier.id, newName = "Cashier Now Owner", newRole = AppRoles.OWNER)
        val promotedCashier = repo.allUsers.first().find { it.id == cashier.id }
        assertEquals(AppRoles.OWNER, promotedCashier?.role)

        // 3. With 3 owners, Owner 1 CAN demote Owner 2 to Manager
        repo.updateStaff(userId = owner2Id, newName = "Owner 2", newRole = AppRoles.MANAGER)
        val demotedOwner2 = repo.allUsers.first().find { it.id == owner2Id }
        assertEquals(AppRoles.MANAGER, demotedOwner2?.role)

        // 4. Owner 1 CAN delete demoted user
        repo.deleteUser(demotedOwner2!!)
        val deleted = repo.allUsers.first().find { it.id == owner2Id }
        assertTrue(deleted == null)
    }
}
