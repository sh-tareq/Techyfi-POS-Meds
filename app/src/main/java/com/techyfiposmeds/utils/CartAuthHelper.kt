package com.techyfiposmeds.utils

import android.content.Context
import android.widget.Toast
import com.techyfiposmeds.R
import com.techyfiposmeds.data.AppPermissions
import com.techyfiposmeds.ui.viewmodel.AuthViewModel

object CartAuthHelper {

    fun canVoidCart(isOwner: Boolean, isSupervisor: Boolean, activePermissions: Set<String>): Boolean {
        return isOwner || activePermissions.contains(AppPermissions.SALES_VOID_CART)
    }

    fun canRemoveItem(isOwner: Boolean, isSupervisor: Boolean, activePermissions: Set<String>): Boolean {
        return isOwner || activePermissions.contains(AppPermissions.SALES_VOID_CART)
    }

    fun handleVoidCartRequest(
        context: Context,
        isOwner: Boolean,
        isSupervisor: Boolean,
        activePermissions: Set<String>,
        authViewModel: AuthViewModel?,
        onAllowed: () -> Unit,
        onRequestSupervisorAuth: (description: String) -> Unit
    ) {
        if (canVoidCart(isOwner, isSupervisor, activePermissions)) {
            onAllowed()
        } else if (authViewModel != null) {
            onRequestSupervisorAuth(context.getString(R.string.supervisor_auth_desc_void_cart))
        } else {
            Toast.makeText(context, context.getString(R.string.permission_required_void_cart), Toast.LENGTH_SHORT).show()
        }
    }

    fun handleRemoveItemRequest(
        context: Context,
        productName: String,
        isOwner: Boolean,
        isSupervisor: Boolean,
        activePermissions: Set<String>,
        authViewModel: AuthViewModel?,
        onAllowed: () -> Unit,
        onRequestSupervisorAuth: (description: String) -> Unit
    ) {
        if (canRemoveItem(isOwner, isSupervisor, activePermissions)) {
            onAllowed()
        } else if (authViewModel != null) {
            onRequestSupervisorAuth(context.getString(R.string.supervisor_auth_desc_remove_item, productName))
        } else {
            Toast.makeText(context, context.getString(R.string.permission_required_remove_item), Toast.LENGTH_SHORT).show()
        }
    }
}
