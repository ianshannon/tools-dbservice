package com.q4.backoffice.tools.restapi.permissions;

import com.openbet.backoffice.security.sdk.CoreSecuritySDK;
import com.openbet.backoffice.security.sdk.api.CoreSecurity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 *Handle Permissions - wrap calls to coreSecurity
 *include 'not-ready handling'
 * either block on creation of coreSecurity object
 * or block on call for permission
 * (or return default Permissions until we start receiving Permissions)
 *
 */
public class Permissions {

	//***********************************************************************************
	//region data
	//***********************************************************************************
	private static final Logger LOG = LoggerFactory.getLogger(Permissions.class);
	private static String module = "\tID-PERM\t";

	private CoreSecurity coreSecurity;
	private boolean coreSecuritySignalled = false;

	private static boolean _blockOnConstruction = false;		//block on construction
	private static boolean _blockOnFirstUse = false;        //block for coreSecurity (if not ready with permissions)

	private boolean blockOnConstruction = false;
	private boolean blockOnFirstUse = false;

	/**
	 * Permission Prototypes
	 *
	 * Enum to use for Permissions in this App
	 */
	public enum PermissionItem {
		identities,
		allidentities
	}

	//***********************************************************************************
	//endregion data
	//***********************************************************************************
	//***********************************************************************************
	//region Singleton Constructor
	//***********************************************************************************
	public static Permissions getInstance(boolean blockOnConstruction, boolean blockOnFirstUse) {
		_blockOnConstruction = blockOnConstruction;
		_blockOnFirstUse = blockOnFirstUse;
		return Permissions.LazyHolder.INSTANCE;
	}
	private static volatile Permissions instance = null;
	private static class LazyHolder {
		static final Permissions INSTANCE = new Permissions();
	}
	private Permissions() {
		try {
			initialise();
		} catch (Exception ex) {
			LOG.error("{}Constructor Exception:{}", module, ex.getMessage());
		}
	}
	//***********************************************************************************
	//endregion Singleton Constructor
	//***********************************************************************************
	//***********************************************************************************
	//region Initialisation
	//***********************************************************************************
	private boolean initialise() {

		blockOnConstruction = _blockOnConstruction;		//blocking params are only used when singleton object is created
		blockOnFirstUse = _blockOnFirstUse;

		LOG.debug("{}CoreSecurity call GetInstance()", module);
		coreSecurity = CoreSecuritySDK.getInstance();
		waitForCoreSecurityPermissions(blockOnConstruction);		//either block now until Permissions are ready - or wait for Signal when Permissions are ready

		return true;
	}
	//***********************************************************************************
	//endregion Initialisation
	//***********************************************************************************
	public boolean isConnected() {
		return coreSecuritySignalled;
	}
	public boolean permissionGranted(String userid, Enum name) {
		checkIfPermissionsReady();
		return coreSecurity.permissionGranted(userid, name);
	}
	public boolean permissionGranted(String userid, String name) {
		checkIfPermissionsReady();
		return coreSecurity.permissionGranted(userid, name);
	}

	public String permissionString(String userid, String name) {
		checkIfPermissionsReady();
		return coreSecurity.permissionString(userid, name);
	}

	public String metrics() {
		checkIfPermissionsReady();
		return coreSecurity.metrics();
	}

	public boolean checkedPermissionGranted(String userid, Enum name) {
		checkIfPermissionsReady();
		try {
			return coreSecurity.permissionGranted(userid, name);
		} catch (IllegalArgumentException ex) {
			return false;
		}
	}
	public boolean checkedPermissionGranted(String userid, String name) {
		checkIfPermissionsReady();
		try {
			return coreSecurity.permissionGranted(userid, name);
		} catch (IllegalArgumentException ex) {
			return false;
		}
	}
	private void checkIfPermissionsReady() {
		if (!coreSecurity.permissionsReady() && (blockOnFirstUse || blockOnConstruction)) {	//note - may have timed out on Construction
			waitForCoreSecurityPermissions(true);
		}
	}

	private void waitForCoreSecurityPermissions(boolean block) {

		CompletableFuture<Void> completeWhenPermissionsReady = coreSecurity.whenReady();
		if (block) {
			LOG.debug("{}CoreSecurity Block until ready()", module);
			try {
				completeWhenPermissionsReady.exceptionally((throwable) -> {
					LOG.error("{}CoreSecurity .whenReady Error", module);
					return null;
				}).get(1000, TimeUnit.MILLISECONDS);
				coreSecuritySignalled = true;
				LOG.debug("{}CoreSecurity ready", module);
			} catch (TimeoutException ex) {
				LOG.error("{}CoreSecurity CheckIfReady - TimeOut", module, ex.toString());
			} catch (Exception ex) {
				LOG.error("{}CoreSecurity CheckIfReady - Exception:{}", module, ex.getMessage());
			}
		} else {
			completeWhenPermissionsReady.exceptionally((throwable) -> {
				LOG.error("{}CoreSecurity .whenReady Error", module);
				return null;
			}).thenRun(() -> {
				LOG.info("{}CoreSecurity has Signalled Ready", module);
				coreSecuritySignalled = true;
			});        											//Log 'CoreSecurity IsReady' - when its Ready!

		}
	}
}

