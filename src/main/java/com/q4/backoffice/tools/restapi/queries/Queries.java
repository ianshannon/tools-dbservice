package com.q4.backoffice.tools.restapi.queries;

import com.q4.backoffice.tools.restapi.db.DbQuery;
import com.q4.backoffice.tools.restapi.permissions.Permissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.stream.Collectors;

public class Queries {
	private static final Logger LOG = LoggerFactory.getLogger(Queries.class);
	private static String module = "\tTL-QURY\t";

	private String queriesDirectory;
	private Permissions permissions;

	private Map<String, DbQuery> dbQueries = new HashMap<>();

	private long previousAccessTime = -1;
	private NavigableMap<String,String> queriesNavigableMap;

//	private String driverName;
//	private String url;

	private Queries(){}
	public Queries(String queriesDirectory, String driverName, String url) {
		this.queriesDirectory = queriesDirectory;
		permissions = Permissions.getInstance(false, true);	//params only used on 1st call to getInstance()

//		this.driverName = driverName;
//		this.url = url;

		if (!driverName.isEmpty()) {
			tryLoadQueries();
		}
	}

	public String queryFor(String userid, String queryName, DbQuery.ResponseFormat responseFormat) throws AccessDeniedException {
		String result = "";
		try {
			tryLoadQueries();
			//queryName can be "identities/1" - need to match "identities/" and use '1' as first param
			//params can only be alphaNumeric - plus ','
			String queryParams = alphaNumericAnd(stringRight(queryName, "/", true),",");
			String useQueryName = stringLeft(queryName, "/", false).toLowerCase();
			String queryText;
			if (queryParams.isEmpty()) {
				queryText = queriesNavigableMap.getOrDefault(useQueryName, "");
			} else {
				queryText = queriesNavigableMap.getOrDefault(useQueryName + "/", "");
				if (queryText.isEmpty()) {
					queryText = queriesNavigableMap.getOrDefault(useQueryName, "");
					if (!queryText.isEmpty() && queryText.contains("?")) {
						queryText = queryText.replace("?", queryParams);
						queryParams = "";
					}
				}
			}
			if (!queryText.isEmpty()) {

				String keyForQuery = stringLeft(queryText, ":", true);
				String select = queryText.substring(keyForQuery.isEmpty() ? 0 : keyForQuery.length()+2);
				String selectType = stringLeft(select," ").toLowerCase();
				DbQuery dbQuery = dbForQuery(queryText);

				switch (selectType){
					case "file":
						result = fileSelect(select.substring(5) , responseFormat, queryName, queryParams);
						break;
					case "select":
						result = dbSelect(dbQuery, select, responseFormat, queryName, queryParams);
						break;
					case "counts":
						result = "table\tCount\n" +
							Arrays.stream(select.split(","))
								//.map((tableName) -> tableName + "\t" + dbQuery.Select("select count(*) as count from " + tableName, "","", "", ""))
								.map((tableName) -> tableName + "\t" + dbQuery.Select("select count(*) as count from " + tableName, DbQuery.ResponseFormat.NONE,"","",false))
								.collect(Collectors.joining("\n"));
						break;
					case "keys":
						result = "table\tKeys\n" +
							Arrays.stream(select.split(","))
								.map((tableName) -> tableName + "\t" + dbQuery.foreignKeys(tableName, "\t", "\n"))
								.collect(Collectors.joining("\n"));
						break;
					case "queries":
						result = queriesNavigableMap.entrySet().stream()
								.map((e) -> e.getKey() + "\t" + e.getValue())
								.collect(Collectors.joining("\n"));
						break;
					default:

				}

//				switch (stringLeft(query, "(").toLowerCase()) {
//					case "counts":
//						query = stringMid(query, "(",")");
//						result = "table\tCount\n" +
//								Arrays.stream(query.split(","))
////								.map((tableName) -> tableName + "\t" + dbQuery.Select("select count(*) as count from " + tableName, "","", "", ""))
//								.map((tableName) -> tableName + "\t" + dbQuery.Select("select count(*) as count from " + tableName, DbQuery.ResponseFormat.NONE,"","",false))
//								.collect(Collectors.joining("\n"));
//						break;
//					case "keys":
//						query = stringMid(query, "(",")");
//						result = "table\tKeys\n" +
//								Arrays.stream(query.split(","))
//										.map((tableName) -> tableName + "\t" + dbQuery.foreignKeys(tableName, "\t", "\n"))
//										.collect(Collectors.joining("\n"));
//						break;
//					case "queries":
//						result = queriesNavigableMap.entrySet().stream()
//										.map((e) -> e.getKey() + "\t" + e.getValue())
//										.collect(Collectors.joining("\n"));
//						break;
//					default:
//
//						String queryRepeats = stringMid(query, "{", "}");
//						if (queryRepeats.isEmpty()) {
//							result = dbQuery.Select(query, responseFormat, queryName, queryParams, true);
//						} else {
//							String subQuery = stringReplace(query,"{","}", "?");
//							result = Arrays.stream(queryRepeats.split(","))
//									.map((e) -> e + "\t" +
//											dbQuery.Select(subQuery.replace("?", e), DbQuery.ResponseFormat.NONE,"", "", false))
//									.collect(Collectors.joining("\n"));
//						}
//				}
				LOG.info("{}REQUEST\t{}\tRequest by {}\tresult ({})", module, queryName, userid, result.substring(0,result.length() < 20 ? result.length(): 20).replace("\n"," ") + "...");	//will return reportSource as empty string
			}
			switch (responseFormat) {
				case JSON:
				case TSV:
				case CSV:
					break;		//already json or tsv or csv
				case HTML:
				default:
					result = queryName + "\n" + result;		//add query as top line of Table
					result = HtmlPage("Query", toTable(result, "\t", "\n", ""));
			}
		} catch (Exception ex) {
			LOG.error("{}REQUEST\t{}\tInvalid request by {}\tException:{}", module, queryName, userid, ex.getMessage());	//will return reportSource as empty string
		}
		return result;
	}

	private DbQuery dbForQuery(String query) {
		String key = stringLeft(query,":",true);
		if (key.isEmpty()) {
			key = "driver@url";
		}
		return dbQueries.computeIfAbsent(key, (newKey) -> {

			int p = newKey.indexOf("@");

			String driverNameKey = p>=0 ? stringLeft(newKey,"@") : "driver" + (newKey.isEmpty() ? "" : "_" + newKey);
			String urlKey = p>=0 ? stringRight(newKey,"@") : "url" + (newKey.isEmpty() ? "" : "_" + newKey);

			String driverName = queriesNavigableMap.ceilingEntry(driverNameKey) != null ? queriesNavigableMap.ceilingEntry(driverNameKey).getValue() : "";
			String url = queriesNavigableMap.ceilingEntry(urlKey) != null ? queriesNavigableMap.ceilingEntry(urlKey).getValue() : "";

			return new DbQuery(driverName, url);
		});
	}
	private String fileSelect(String fileName, DbQuery.ResponseFormat responseFormat, String queryName, String queryParams) {
		String result;
		String path = String.format("%s/%s", queriesDirectory, fileName);
		try {
			result = loadFile(path, false);
		} catch (FileNotFoundException e) {
			result = "NotFound";
		}

		return result;
	}
	private String dbSelect(DbQuery dbQuery, String select, DbQuery.ResponseFormat responseFormat, String queryName, String queryParams) {
		String queryRepeats = stringMid(select, "{", "}");
		if (queryRepeats.isEmpty()) {
			return dbQuery.Select(select, responseFormat, queryName, queryParams, true);
		} else {
			if (queryRepeats.equals("?")) {
				String temp = select.replace("{?}", queryParams);
				return dbQuery.Select(temp, responseFormat, queryName, "", false);
			} else {
				String subQuery = stringReplace(select, "{", "}", "?");
				return Arrays.stream(queryRepeats.split(","))
						.map((e) -> e + "\t" + dbQuery.Select(subQuery.replace("?", e), DbQuery.ResponseFormat.NONE, "", "", false))
						.collect(Collectors.joining("\n"));
			}
		}
	}
	private void tryLoadQueries() {
		try {
			long currentAccessTime = 0;
			String path = String.format("%s/%s.txt", queriesDirectory, "queries_1");

			FileTime fileTime = Files.getLastModifiedTime(Paths.get(path));        //will throw IO exception if file not found
			currentAccessTime = fileTime.toMillis();
			if (queriesNavigableMap==null || previousAccessTime < currentAccessTime) {
				previousAccessTime = currentAccessTime;
				queriesNavigableMap = Files.readAllLines(Paths.get(path)).stream()
						.map((line) -> line.split("\t"))
						.filter((lines) -> {
							if (lines.length != 2) {
								return false;
							} else {
								switch (stringLeft(lines[0], "_").toLowerCase()) {
									case "driver":
									case "url":
									default:
										return true;
								}
							}
						})
						.collect(
								Collectors.toMap(
										(lines) -> lines[0].toLowerCase(),
										(lines) -> lines[1],
										(v1, v2) -> v1,
										TreeMap::new			//NOTE - if we need threading support use ConcurrentSkipListMap
								));

				String driverName = queriesNavigableMap.ceilingEntry("driver") != null ? queriesNavigableMap.ceilingEntry("driver").getValue() : "";
				String url = queriesNavigableMap.ceilingEntry("url") != null ? queriesNavigableMap.ceilingEntry("url").getValue() : "";

				dbQueries.clear();		//just clear map (for now)

//				if (!this.driverName.equals(driverName) || !this.url.equals(url)) {
//					this.driverName = driverName;
//					this.url = url;
////					dbQuery = null;
//				}
				//NOTE - url can contain unprotected password - WE CAN BE LOGGING PASSWORD HERE !!!!
				LOG.debug("{}LoadQueries\t{} Queries loaded\t(driver={},url={})", module, queriesNavigableMap.size(), driverName, url);

			}
		} catch (IOException ex) {
			LOG.error("{}LoadQueries\tException: {}", module, ex.getMessage());
		}
	}
	/**
	 * Utility to load a file as a string
	 *
	 * @param path - location of file
	 * @param throwFileNotFoundException - throw exception if file not found
	 * @return the contents of the file
	 */
	private String loadFile(String path, boolean throwFileNotFoundException) throws FileNotFoundException {
		try {
			return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
		} catch (Exception ex) {

			String message = String.format("loading Text File (%s) Exception:%s", path, ex.getMessage());
			try {
				message += " (current directory " + new String(new java.io.File( "." ).getCanonicalPath()) + ")";
			} catch (Exception e) {	//ignore any exception here!
			}

			LOG.warn(message);
			if (throwFileNotFoundException) {
				throw new FileNotFoundException(message);
			}
			return message;
		}
	}
	private String alphaNumericAnd(String source, String otherValidChars) {
		StringBuilder sb = new StringBuilder(source);
		for (int i = sb.length() -1; i>=0 ; i--) {
			char c = source.charAt(i);
			if (!((c>='a' && c<='z') || (c>='A' && c<='Z') || (c>='0' && c<='9') || otherValidChars.indexOf(c)>=0 )) {
				sb.deleteCharAt(i);
			}
		}
		return sb.toString();
	}
	private String stringLeft(String source, String key){
		return stringLeft(source, key, false);
	}
	private String stringLeft(String source, String key, boolean emptyIfNotFound){
		int posOfKey = source.indexOf(key);
		if (posOfKey>0) {
			return source.substring(0, posOfKey);
		}
		return emptyIfNotFound ? "" : source;
	}
	private String stringRight(String source, String key){
		return stringRight(source,key,false);
	}
	private String stringRight(String source, String key, boolean emptyIfNotFound){
		int posOfKey = source.indexOf(key);
		if (posOfKey>0) {
			return source.substring(posOfKey + key.length());
		}
		return emptyIfNotFound ? "" : source;
	}
	private String stringMid(String source, String key, String endMarker) {
		int posOfKey = source.indexOf(key);
		if (posOfKey>0) {
			int posOfEndMarker = source.indexOf(endMarker);
			if (posOfEndMarker<0) {
				posOfEndMarker = source.length();
			}
			return source.substring(posOfKey + key.length(), posOfEndMarker);
		}
		return "";
	}
	private String stringReplace(String source, String key, String endMarker, String replacement) {
		int posOfKey = source.indexOf(key);
		if (posOfKey>0) {
			int posOfEndMarker = source.indexOf(endMarker);
			if (posOfEndMarker<0) {
				posOfEndMarker = source.length();
			}
			return source.substring(0, posOfKey) + replacement + source.substring(posOfEndMarker+1);
		}
		return source;
	}

	//***************************************************************************
	//region HTML Page
	//***************************************************************************
	private String HtmlPage(String heading, String body) {
		return HtmlStyle("<!DOCTYPE html><html><head><h1>{heading}</h1></head><body>{body}<div id='response'> </div></body></html>"
				.replace("{heading}", heading)
				.replace("{body}", body));
	}

	private String HtmlStyle(String source) {

		return source.replace("<head>",
				"<head>" +
						"<style>" +
						"table {font-family: arial, sans-serif;border-collapse: collapse;width: 100%;}" +
						"td, th {border: 1px solid #dddddd;text-align: left;padding: 8px;}" +
						"tr:nth-child(even) {background-color: #dddddd;}" +
						"</style>"
		);
	}
	private String toTable(String source, String colCheck, String lineCheck, String colReplace) {
		return "<table><tr><td>" +
				source
						.replace(colCheck, "</td><td>" + colReplace)
						.replace(lineCheck, "</td></tr><tr><td>") +
				"</td></tr></table>";
	}
}
