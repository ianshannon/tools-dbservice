package com.openbet.backoffice.tools.restapi.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public interface AppConfig {

	Config config = ConfigFactory.load();

	static int getInt(String name){
		return getInt(name, 0,true);
	}
	static int getInt(String name, int defaultValue){
		return getInt(name, defaultValue,false);
	}
	static int getInt(String name, int defaultValue, boolean throwIfMissing){
		if (config.hasPath(name)) {
			return config.getInt(name);
		}
		if (throwIfMissing) throw new UnsupportedOperationException("parameter " + name + " not defined");
		return defaultValue;
	}

	static String getString(String name) {
		return getString(name, "", true);
	}
	static String getString(String name, String defaultValue){
		return getString(name, defaultValue,false);
	}
	static String getString(String name, String defaultVaue, boolean throwIfMissing){
		if (config.hasPath(name)) {
			return config.getString(name);
		}
		if (throwIfMissing) throw new UnsupportedOperationException("parameter " + name + " not defined");
		return defaultVaue;
	}
}
