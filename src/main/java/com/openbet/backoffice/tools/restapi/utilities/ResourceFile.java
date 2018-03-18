package com.openbet.backoffice.tools.restapi.utilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

public class ResourceFile {

	static final Logger LOG = LoggerFactory.getLogger(ResourceFile.class);

	/**
	 * Load a resource file - return as a string
	 *
	 * @param resourceName		- Name of Resource
	 * @return					- return resource as a string - on error return (and Log) and error message
	 */
	public String Load(String resourceName) {
		if (!resourceName.isEmpty()) {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/" + resourceName)));
			) {
				return reader.lines().collect(Collectors.joining());// .forEach(s -> strBuild.append(s));
			} catch (Exception ex) {
				String msg = "Unable to Load Resource... for " + resourceName + " (Exception:" + ex.getMessage() + ")";
				LOG.error(msg);
			}
		}
		return "";
	}
}
