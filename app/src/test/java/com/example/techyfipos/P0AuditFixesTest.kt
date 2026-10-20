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
import org.junit.Assert.assertFalse
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
        val originalClock = AuthViewModel.clockProvider
        try {
            var simulatedTime = 100_000L
            AuthViewModel.clockProvider = { simulatedTime }
            AuthViewModel.recordUserActivity()
            assertEquals(100_000L, AuthViewModel.getLastUserActivityElapsed())

            simulatedTime = 250_000L
            AuthViewModel.recordUserActivity()
            assertEquals(250_000L, AuthViewModel.getLastUserActivityElapsed())
        } finally {
            AuthViewModel.clockProvider = originalClock
        }
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

        val enXml = java.io.File("src/main/res/values/strings.xml").readText()
        val enFormatString = """<string name="z_report_invoices_units_fmt">(.*?)</string>""".toRegex().find(enXml)!!.groupValues[1]
        val formattedEn = String.format(java.util.Locale.US, enFormatString, 5, enWhole)
        assertEquals("Invoices: 5 | Units: 15", formattedEn)

        val bnXml = java.io.File("src/main/res/values-bn/strings.xml").readText()
        val bnFormatString = """<string name="z_report_invoices_units_fmt">(.*?)</string>""".toRegex().find(bnXml)!!.groupValues[1]
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

        // Test real DataRepository discount override validation
        val cashier = User(id = 1L, name = "Cashier", pinHash = hashSha256("1111"), role = AppRoles.CASHIER, maxDiscountPercent = 10.0, isActive = true)
        val owner = User(id = 2L, name = "Owner", pinHash = hashSha256("9999"), role = AppRoles.OWNER, maxDiscountPercent = 100.0, isActive = true)
        val (repo, _) = createRepositoryWithUsers(listOf(cashier, owner))
        repo.setSessionUser(cashier)

        // Set approved discount override on repository for initialCart hash
        repo.grantDiscountOverride(maxDiscountAmount = 50.0, cartHash = initialHash)

        // Override is valid for initial hash
        assertTrue(repo.hasValidDiscountOverride(50.0, initialHash))

        // Override is INVALID when cart items, quantity, or price mode change (hash mismatch)
        assertFalse(repo.hasValidDiscountOverride(50.0, modifiedHash))
        assertFalse(repo.hasValidDiscountOverride(50.0, modifiedQtyHash))
        assertFalse(repo.hasValidDiscountOverride(50.0, modifiedModeHash))

        // Override is INVALID when requested discount exceeds approved discount
        assertFalse(repo.hasValidDiscountOverride(60.0, initialHash))
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
    fun testR23_01_lockScreenBruteForcePrevention_cannotBypassWithOwnPin() = kotlinx.coroutines.test.runTest {
        val cashier = User(id = 1L, name = "Cashier", pinHash = hashSha256("1111"), role = AppRoles.CASHIER, maxDiscountPercent = 10.0, isActive = true)
        val owner = User(id = 2L, name = "Owner", pinHash = hashSha256("9999"), role = AppRoles.OWNER, maxDiscountPercent = 100.0, isActive = true)
        val (repo, _) = createRepositoryWithUsers(listOf(cashier, owner))

        // 4 wrong attempts guessing owner PIN at lock screen
        for (i in 1..4) {
            val res = repo.verifyPinDetailed("0000", clearOnSuccess = true)
            assertTrue("Expected Invalid on attempt $i", res is PinVerifyResult.Invalid)
        }

        // Cashier logs in with own PIN (success), but failed_attempts is not wiped
        val successRes = repo.verifyPinDetailed("1111", clearOnSuccess = true)
        assertTrue("Expected Success on own PIN", successRes is PinVerifyResult.Success)

        // Next failure (5th failure) must trigger lockout immediately
        val res5th = repo.verifyPinDetailed("0000", clearOnSuccess = true)
        assertTrue("5th failure must trigger lock even after successful login", res5th is PinVerifyResult.Locked)
    }

    @Test
    fun testR23_02_permissionMatrix_nonOwnerCannotGrantUnheldOrSystemPermissions() = kotlinx.coroutines.test.runTest {
        val manager = User(
            id = 1L,
            name = "Manager",
            pinHash = hashSha256("1111"),
            role = AppRoles.MANAGER,
            customPermissions = AppPermissions.serializePermissions(
                setOf(AppPermissions.SYSTEM_PERMISSIONS, AppPermissions.SYSTEM_BACKUP_RESTORE, AppPermissions.SALES_SELL)
            ),
            maxDiscountPercent = 10.0,
            isActive = true
        )
        val cashier = User(id = 2L, name = "Cashier", pinHash = hashSha256("2222"), role = AppRoles.CASHIER, maxDiscountPercent = 10.0, isActive = true)
        val owner = User(id = 3L, name = "Owner", pinHash = hashSha256("9999"), role = AppRoles.OWNER, maxDiscountPercent = 100.0, isActive = true)
        val (repo, userDao) = createRepositoryWithUsers(listOf(manager, cashier, owner))

        repo.setSessionUser(manager)

        // Manager cannot grant SYSTEM_* permissions via updateStaffPermissions
        val exSystem = assertThrows(DataRepository.UnauthorizedException::class.java) {
            kotlinx.coroutines.runBlocking {
                repo.updateStaffPermissions(cashier.id, setOf(AppPermissions.SALES_SELL, AppPermissions.SYSTEM_BACKUP_RESTORE))
            }
        }
        assertTrue(exSystem.message?.contains("administrative") == true)

        // Manager cannot grant permissions they do not hold
        val exUnheld = assertThrows(DataRepository.UnauthorizedException::class.java) {
            kotlinx.coroutines.runBlocking {
                repo.updateStaffPermissions(cashier.id, setOf(AppPermissions.SALES_SELL, AppPermissions.REPORTS_VIEW_PNL))
            }
        }
        assertTrue(exUnheld.message?.contains("do not hold") == true)

        // Manager can grant subset of held non-system permissions
        repo.updateStaffPermissions(cashier.id, setOf(AppPermissions.SALES_SELL))
        val updatedCashier = repo.allUsers.first().firstOrNull { it.id == cashier.id }
        val perms = AppPermissions.parsePermissionsJson(updatedCashier?.customPermissions)
        assertEquals(setOf(AppPermissions.SALES_SELL), perms)
    }

    @Test
    fun testR23_03_managerHighDiscountAllowedAndOwnerSelfEditSaves() = kotlinx.coroutines.test.runTest {
        val manager = User(
            id = 1L,
            name = "Manager",
            pinHash = hashSha256("1111"),
            role = AppRoles.MANAGER,
            maxDiscountPercent = 10.0,
            isActive = true
        )
        val owner = User(
            id = 2L,
            name = "Owner",
            pinHash = hashSha256("9999"),
            role = AppRoles.OWNER,
            maxDiscountPercent = 100.0,
            isActive = true
        )
        val (repo, _) = createRepositoryWithUsers(listOf(manager, owner))

        // Manager has SALES_HIGH_DISCOUNT by default, so effective max discount is 100%
        val managerEffectiveMaxDiscount = AppPermissions.getEffectiveMaxDiscount(manager)
        assertEquals(100.0, managerEffectiveMaxDiscount, 0.001)

        // Owner can edit their own account (name and PIN) without being blocked by maxDiscount check
        repo.setSessionUser(owner)
        repo.updateStaff(
            userId = owner.id,
            newName = "Owner Updated",
            newPin = "8888",
            oldPin = "9999",
            maxDiscount = null
        )
        val updatedOwner = repo.allUsers.first().firstOrNull { it.id == owner.id }
        assertEquals("Owner Updated", updatedOwner?.name)
        assertTrue(repo.verifyPinDetailed("8888") is PinVerifyResult.Success)
    }

    @Test
    fun testR23_04_staffDelegateCannotExceedRights() = kotlinx.coroutines.test.runTest {
        val owner = User(id = 1L, name = "Owner", pinHash = hashSha256("9999"), role = AppRoles.OWNER, maxDiscountPercent = 100.0, isActive = true)
        val manager = User(
            id = 2L,
            name = "Manager",
            pinHash = hashSha256("2222"),
            role = AppRoles.MANAGER,
            customPermissions = AppPermissions.serializePermissions(setOf(AppPermissions.SYSTEM_MANAGE_STAFF, AppPermissions.SYSTEM_PERMISSIONS)),
            maxDiscountPercent = 50.0,
            isActive = true
        )
        val cashierDelegate = User(
            id = 3L,
            name = "Cashier Delegate",
            pinHash = hashSha256("3333"),
            role = AppRoles.CASHIER,
            customPermissions = AppPermissions.serializePermissions(setOf(AppPermissions.SYSTEM_MANAGE_STAFF, AppPermissions.SYSTEM_PERMISSIONS)),
            maxDiscountPercent = 10.0,
            isActive = true
        )
        val targetCashier = User(
            id = 4L,
            name = "Target Cashier",
            pinHash = hashSha256("4444"),
            role = AppRoles.CASHIER,
            maxDiscountPercent = 10.0,
            isActive = true
        )
        val (repo, _) = createRepositoryWithUsers(listOf(owner, manager, cashierDelegate, targetCashier))

        // 1. Cashier delegate cannot delete or deactivate a Manager (higher role)
        repo.setSessionUser(cashierDelegate)
        assertThrows(DataRepository.UnauthorizedException::class.java) {
            kotlinx.coroutines.runBlocking {
                repo.deleteUser(manager)
            }
        }
        assertThrows(DataRepository.UnauthorizedException::class.java) {
            kotlinx.coroutines.runBlocking {
                repo.setStaffActive(manager.id, false)
            }
        }
        assertThrows(DataRepository.UnauthorizedException::class.java) {
            kotlinx.coroutines.runBlocking {
                repo.updateStaff(userId = manager.id, newName = "Manager Renamed")
            }
        }
        assertThrows(DataRepository.UnauthorizedException::class.java) {
            kotlinx.coroutines.runBlocking {
                repo.updateStaffPermissions(userId = manager.id, permissions = setOf(AppPermissions.SALES_SELL))
            }
        }

        // 2. Cashier delegate cannot create a Manager
        assertThrows(DataRepository.UnauthorizedException::class.java) {
            kotlinx.coroutines.runBlocking {
                repo.createUser("New Manager", "5555", AppRoles.MANAGER)
            }
        }

        // 3. Cashier delegate creates a Cashier with customPermissions = null
        // Defaults must be intersected with caller's held permissions and exclude system permissions
        val newCashierId = repo.createUser("New Cashier", "5555", AppRoles.CASHIER, customPermissions = null)
        val createdCashier = repo.allUsers.first().firstOrNull { it.id == newCashierId }
        assertNotNull(createdCashier)
        val grantedPermissions = AppPermissions.deserializePermissions(createdCashier?.customPermissions)
        // Cashier delegate only holds SYSTEM_MANAGE_STAFF and SYSTEM_PERMISSIONS, neither of which can be granted to new cashier
        assertTrue("Granted permissions must be empty or subset of non-system caller permissions", grantedPermissions?.isEmpty() == true)

        // 4. When manager changes a user's role, permissions are intersected with caller rights
        repo.setSessionUser(manager)
        repo.updateStaff(userId = targetCashier.id, newName = "Updated Target", newRole = AppRoles.MANAGER, customPermissions = null)
        val updatedTarget = repo.allUsers.first().firstOrNull { it.id == targetCashier.id }
        val updatedPermissions = AppPermissions.deserializePermissions(updatedTarget?.customPermissions)
        assertTrue("Permissions after role change by non-owner must be explicit set", updatedPermissions != null)
        assertTrue("Permissions after role change by non-owner must be intersected with caller rights", updatedPermissions!!.all { repo.hasPermission(it) && !AppPermissions.isSystemPermission(it) })
    }

    @Test
    fun testR23_05_pinCheckOraclesThrottledAndNeutral() = kotlinx.coroutines.test.runTest {
        val owner = User(id = 1L, name = "Owner", pinHash = hashSha256("9999"), role = AppRoles.OWNER, maxDiscountPercent = 100.0, isActive = true)
        val manager = User(
            id = 2L,
            name = "Manager",
            pinHash = hashSha256("5678"),
            role = AppRoles.MANAGER,
            maxDiscountPercent = 50.0,
            isActive = true
        )
        val attacker = User(
            id = 3L,
            name = "Attacker Cashier",
            pinHash = hashSha256("1111"),
            role = AppRoles.CASHIER,
            customPermissions = AppPermissions.serializePermissions(setOf(AppPermissions.SYSTEM_MANAGE_STAFF)),
            maxDiscountPercent = 10.0,
            isActive = true
        )
        val (repo, _) = createRepositoryWithUsers(listOf(owner, manager, attacker))
        repo.setSessionUser(owner)

        // 1. Wrong old PIN guesses in updateStaff return neutral "Invalid or unavailable PIN" and trigger lockout
        for (i in 1..4) {
            val ex = assertThrows(IllegalStateException::class.java) {
                kotlinx.coroutines.runBlocking {
                    repo.updateStaff(userId = manager.id, newName = "Manager", newPin = "4321", oldPin = "000$i")
                }
            }
            assertEquals("Invalid or unavailable PIN", ex.message)
        }

        // 5th wrong old PIN triggers lockout
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                repo.updateStaff(userId = manager.id, newName = "Manager", newPin = "4321", oldPin = "0005")
            }
        }
        assertTrue("Lockout must be active after 5 failed old-PIN attempts", repo.loginLockRemainingMillis() > 0L)

        // 6th attempt is blocked by lockout
        val lockedEx = assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                repo.updateStaff(userId = manager.id, newName = "Manager", newPin = "4321", oldPin = "5678")
            }
        }
        assertTrue("Expected lockout message", lockedEx.message?.contains("locked out") == true)
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
        assertEquals("Alert cleared after acknowledgment because recovery PIN counts as the change", 0L, repo.getUnacknowledgedOwnerPinResetTimestamp())

        // Owner can also update PIN
        repo.updateStaff(userId = owner.id, newName = "Owner", newPin = "3333", oldPin = "2222")
        assertEquals(0L, repo.getUnacknowledgedOwnerPinResetTimestamp())

        // Permanent audit record remains in Settings
        val audit = repo.getOwnerPinResetAudit()
        assertNotNull(audit)
        assertTrue(audit!!.acknowledged)
        assertTrue(audit.pinChangedAfterReset)
    }

    @Test
    fun testR23_06_multiOwnerSafetyAndAlerts() = kotlinx.coroutines.test.runTest {
        val owner1 = User(id = 1L, name = "Owner 1", pinHash = hashSha256("1111"), role = AppRoles.OWNER, maxDiscountPercent = 100.0, isActive = true)
        val owner2 = User(id = 2L, name = "Owner 2", pinHash = hashSha256("2222"), role = AppRoles.OWNER, maxDiscountPercent = 100.0, isActive = true)
        val inactiveOwner = User(id = 3L, name = "Inactive Owner", pinHash = hashSha256("3333"), role = AppRoles.OWNER, maxDiscountPercent = 100.0, isActive = false)
        val (repo, _) = createRepositoryWithUsers(listOf(owner1, owner2, inactiveOwner))

        // 1. Device credential reset with multiple active owners requires valid targetOwnerId
        val failNoTarget = repo.resetOwnerPinAfterDeviceCredential("4444", null)
        assertTrue("Must fail when targetOwnerId is null and multiple active owners exist", !failNoTarget)

        val failInactiveTarget = repo.resetOwnerPinAfterDeviceCredential("4444", inactiveOwner.id)
        assertTrue("Must fail on inactive owner id", !failInactiveTarget)

        val failInvalidTarget = repo.resetOwnerPinAfterDeviceCredential("4444", 9999L)
        assertTrue("Must fail on non-existent owner id", !failInvalidTarget)

        // 2. Valid target succeeds and sets audit metadata
        val successReset = repo.resetOwnerPinAfterDeviceCredential("5555", owner1.id)
        assertTrue(successReset)

        val audit = repo.getOwnerPinResetAudit()
        assertNotNull(audit)
        assertEquals(owner1.id, audit?.resetOwnerId)
        assertEquals("Owner 1", audit?.resetOwnerName)
        assertTrue(audit?.pinChangedAfterReset == true)
        assertTrue(audit?.acknowledged == false)

        // 3. Alert is targeted to Owner 1, not Owner 2
        assertEquals(0L, repo.getUnacknowledgedOwnerPinResetTimestamp(owner2.id))
        val owner1AlertTs = repo.getUnacknowledgedOwnerPinResetTimestamp(owner1.id)
        assertTrue(owner1AlertTs > 0L)

        // Owner 1 acknowledges
        repo.setSessionUser(owner1)
        repo.acknowledgeOwnerPinReset(owner1.id)
        assertEquals(0L, repo.getUnacknowledgedOwnerPinResetTimestamp(owner1.id))
        assertEquals(owner1.id, repo.getOwnerPinResetAudit()?.acknowledgedBy)

        // 4. Inactive owner can be edited when only one owner is active
        // Demote owner2 so only owner1 is active
        repo.updateStaff(userId = owner2.id, newName = "Demoted Owner 2", newRole = AppRoles.MANAGER)
        // Now only owner1 is active, inactiveOwner is inactive. Editing inactive owner must succeed.
        repo.updateStaff(userId = inactiveOwner.id, newName = "Inactive Owner Renamed")
        val updatedInactive = repo.allUsers.first().firstOrNull { it.id == inactiveOwner.id }
        assertEquals("Inactive Owner Renamed", updatedInactive?.name)
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

    @Test
    fun testR23_07_discountAndCreditOverrideLifecycle() = kotlinx.coroutines.test.runTest {
        val owner = User(id = 1L, name = "Owner", pinHash = hashSha256("1111"), role = AppRoles.OWNER, maxDiscountPercent = 100.0, isActive = true)
        val cashier = User(id = 2L, name = "Cashier", pinHash = hashSha256("2222"), role = AppRoles.CASHIER, maxDiscountPercent = 10.0, isActive = true)
        val (repo, _) = createRepositoryWithUsers(listOf(owner, cashier))

        // 1. Discount override granted by supervisor
        val authOk = repo.authorizeDiscountOverride("1111", 50.0, "cart-hash-1")
        assertTrue(authOk)
        assertTrue(repo.hasValidDiscountOverride(50.0, "cart-hash-1"))
        assertFalse(repo.hasValidDiscountOverride(55.0, "cart-hash-1"))
        assertFalse(repo.hasValidDiscountOverride(50.0, "cart-hash-2"))

        // 2. User session switch / logout clears discount and other override tokens
        repo.setSessionUser(cashier)
        assertFalse(repo.hasValidDiscountOverride(50.0, "cart-hash-1"))

        // 3. Credit limit override lifecycle and amount clamping
        repo.authorizeCreditLimitOverride("1111", "01711111111", 300.0, "hash-due")
        assertTrue(repo.hasValidCreditLimitOverride("01711111111", requestedAmount = 300.0, cartHash = "hash-due"))
        assertFalse(repo.hasValidCreditLimitOverride("01711111111", requestedAmount = 350.0, cartHash = "hash-due"))
        assertFalse(repo.hasValidCreditLimitOverride("01899999999", requestedAmount = 100.0))

        // Session clear clears credit override
        repo.clearSession()
        assertFalse(repo.hasValidCreditLimitOverride("01711111111", requestedAmount = 300.0, cartHash = "hash-due"))

        // 4. Hold cart supervisor override authorization
        assertFalse(repo.authorizeHoldCartOverride("9999")) // invalid PIN
        assertTrue(repo.authorizeHoldCartOverride("1111")) // valid supervisor PIN
        assertTrue(repo.consumeHoldCartOverride())
        assertFalse(repo.consumeHoldCartOverride()) // consumed
    }
}
