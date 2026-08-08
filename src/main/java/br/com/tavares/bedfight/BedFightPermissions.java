package br.com.tavares.bedfight;

import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.permissions.Permissions;

public final class BedFightPermissions {
	private static final Permission ADMIN_PERMISSION = Permission.Atom.create("bedfight.admin.use");

	private BedFightPermissions() {
	}

	public static boolean hasAdminPermission(PermissionSet permissions) {
		if (permissions.hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
			return true;
		}
		return permissions.hasPermission(ADMIN_PERMISSION);
	}
}
