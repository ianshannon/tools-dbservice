package com.openbet.backoffice.tools.restapi.db;

import com.openbet.backoffice.tools.restapi.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class DbQuery {
	private static final Logger LOG = LoggerFactory.getLogger(DbQuery.class);
	private static String module = "\tTL-DBQU\t";

	/**
	 * Data Formats - JSON,CSV,TSV supported direct from SQL results
	 */
	public enum ResponseFormat {
		JSON,CSV,TSV,HTML,NONE
	}
	private String driverName;
	private String url;

	private final int SB_INITIAL_CAPACITY_QUERY = 512;
	private final String JSON_OBJECTARRAY_TEMPLATE = "{\"%s\":[{%s}]}";
	private final String JSON_OBJECT_TEMPLATE = "{%s}";
	private final String JSON_ITEM_TEMPLATE = "\"%s\":\"%s\"";
	private final String JSON_VARIABLE_SPLIT = ",";
	private final String JSON_ROW_SPLIT = "},{";

	private DbQuery() {}
	public DbQuery(String className, String url){
		try {
			this.driverName = className;
			this.url = url;
			if (!className.isEmpty()) {
				Class.forName(driverName);
			}
			LOG.info("{}Database Loaded driver ({})", module, driverName);
		} catch (ClassNotFoundException ex) {
			LOG.error("{}Database Driver Not found:{}", module, driverName);
		}
	}
	public String Select(String sqlQuery, ResponseFormat responseFormat, String queryName, String params, boolean asArray) {
		switch (responseFormat) {
			case CSV:
				return Select(sqlQuery, queryName, ",", "\n", "", params, true);
			case JSON:
				String result = Select(sqlQuery, queryName, JSON_VARIABLE_SPLIT, JSON_ROW_SPLIT, JSON_ITEM_TEMPLATE, params, false);
				if (result.isEmpty()) {
					return "";
				} else if (asArray) {
					return String.format(JSON_OBJECTARRAY_TEMPLATE, queryName, result);
				} else {
					return String.format(JSON_OBJECT_TEMPLATE, result);
				}
			case NONE:
				return Select(sqlQuery, queryName, "", "", "", params, false	);
			case TSV:
			default:
				return Select(sqlQuery, queryName, "\t", "\n", "", params, true	);
		}
	}
	private String Select(String sqlQuery, String queryName, String columnSplitter, String rowSplitter, String itemTemplate, String params, boolean includeHeaders) {
		StringBuilder sb = new StringBuilder(SB_INITIAL_CAPACITY_QUERY);

		if (driverName.isEmpty()) {
			try {
				sb.append(AppConfig.getString(url + "." + queryName));
			} catch (Exception ex) {
				sb.append("{}");
			}
			LOG.debug("{}MOCK (No Driver)\t{}\t{}\t{}", module, sqlQuery, queryName, sb.toString());
		} else {
			if (!sqlQuery.substring(0, 7).equalsIgnoreCase("select ")) {
				sb.append("Invalid Format");
			} else {
				Instant startInstant = Instant.now();
				try (	Connection connection = preparedConnection(url, false);
						PreparedStatement statement = preparedStatement(connection, sqlQuery, params);
						ResultSet resultSet = statement.executeQuery()
				) {
					long timeToPrepare = ChronoUnit.MILLIS.between(startInstant, Instant.now());

					ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
					int colCount = resultSetMetaData.getColumnCount();
					int rowCount = 0;
					if (includeHeaders) {
						for (int i = 1; i <= colCount; i++) {
							if (i > 1)
								sb.append(columnSplitter);
							sb.append(resultSetMetaData.getColumnName(i));
						}
						sb.append(rowSplitter);
					}
					while (resultSet.next()) {
						if (rowCount++ > 0 && !rowSplitter.isEmpty())
							sb.append(rowSplitter);
						for (int i = 1; i <= colCount; i++) {
							if (i > 1)
								sb.append(columnSplitter);
							String result;
							switch (resultSetMetaData.getColumnType(i)) {
								case Types.VARCHAR:                    //varchar
								case Types.CHAR:                    //char
								case Types.TIMESTAMP:                //datetime year to second
								case Types.DATE:
									result = resultSet.getString(i);
									break;
								case Types.SMALLINT:
								case Types.INTEGER:                    //int or serial
									result = String.valueOf(resultSet.getInt(i));
									break;
								case Types.DECIMAL:                    //decimal
									result = String.valueOf(resultSet.getBigDecimal(i));
									break;
								case Types.FLOAT:
								case Types.DOUBLE:        //NOTE - the 'name' of a DOUBLE (value 8) is reported as 'FLOAT' in Informix driver!!!!'
									result = String.valueOf(resultSet.getFloat(i));
									break;
								default:
									result = "ColumnType not yet supported(" + resultSetMetaData.getColumnTypeName(i) + "=" + String.valueOf(resultSetMetaData.getColumnType(i)) + ")";
							}
							if (itemTemplate.isEmpty()) {
								sb.append(result != null ? result : "null");
							} else {
								sb.append(String.format(itemTemplate, resultSetMetaData.getColumnName(i), result != null ? result : "null"));
							}
						}
					}
					//will only calc duration if it is being logged!
					LOG.debug("{}Database Query returned {} Rows of {} columns in {} ms ({})", module, rowCount, colCount, ChronoUnit.MILLIS.between(startInstant, Instant.now()), timeToPrepare);
				} catch (SQLException ex) {
					LOG.error("{}Database SQLException:{}\t{}", module, ex.getMessage(), sqlQuery);
				}
			}
		}
		return sb.toString();
	}
	private Connection preparedConnection(String url, boolean autoCommit) throws SQLException {
		Connection connection = DriverManager.getConnection(url);
		connection.setAutoCommit(autoCommit);
		return connection;
	}
	private PreparedStatement preparedStatement(Connection connection, String sqlQuery, String params) throws SQLException {
		PreparedStatement preparedStatement = connection.prepareStatement(sqlQuery);
		if (sqlQuery.indexOf("?")>0 && !params.isEmpty()) {
			preparedStatement.setString(1, params);
		}
		return preparedStatement;
	}
	public String foreignKeys(String tableName, String columnSeperator, String rowSeperator) {
		StringBuilder sb = new StringBuilder();

		Instant startInstant = Instant.now();
		ResultSet resultSet = null;
		try (
				Connection connection = preparedConnection(url, false);//, user, password);
		) {
			DatabaseMetaData databaseMetaData = connection.getMetaData();
			long timeToPrepare = ChronoUnit.MILLIS.between(startInstant, Instant.now());

			sb.append("databaseName=").append(databaseMetaData.getDatabaseProductName()).append(rowSeperator);

			resultSet = databaseMetaData.getImportedKeys(null, null, tableName);

			ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
			int colCount = resultSetMetaData.getColumnCount();
			int rowCount = 0;

			while(resultSet.next()){

				if (rowCount++>0 && !rowSeperator.isEmpty()) sb.append(rowSeperator);
				for (int i = 1; i <= colCount; i++) {
					if (i>1) sb.append(columnSeperator);
					String result = "?";
					switch (resultSetMetaData.getColumnType(i)) {
						case Types.VARCHAR:					//varchar
						case Types.CHAR:					//char
						case Types.TIMESTAMP:				//datetime year to second
						case Types.DATE:
							result = resultSet.getString(i);
							break;
						case Types.SMALLINT:
						case Types.INTEGER:					//int or serial
							result = String.valueOf(resultSet.getInt(i));
							break;
						case Types.DECIMAL:					//decimal
							result = String.valueOf(resultSet.getBigDecimal(i));
							break;
						case Types.FLOAT:
						case Types.DOUBLE:		//NOTE - the 'name' of a DOUBLE (value 8) is reported as 'FLOAT' in Informix driver!!!!'
							result = String.valueOf(resultSet.getFloat(i));
							break;
						case Types.ARRAY:
						case Types.BIGINT:
						case Types.BINARY:
						case Types.BIT:
						case Types.BLOB:
						case Types.BOOLEAN:
						case Types.CLOB:
						case Types.DATALINK:
						case Types.DISTINCT:
						case Types.JAVA_OBJECT:
						case Types.LONGNVARCHAR:
						case Types.LONGVARBINARY:
						case Types.LONGVARCHAR:
						case Types.NCHAR:
						case Types.NCLOB:
						case Types.NULL:
						case Types.NUMERIC:
						case Types.NVARCHAR:
						case Types.OTHER:
						case Types.REAL:
						case Types.REF:
						case Types.REF_CURSOR:
						default:
							result = "ColumnType not yet supported(" + resultSetMetaData.getColumnTypeName(i) + "=" +String.valueOf(resultSetMetaData.getColumnType(i)) + ")";
					}
				}
			}
			//will only calc duration if it is being logged!
			LOG.debug("{}Schema Query returned {} Rows of {} columns in {} ms ({})", module, rowCount, colCount, ChronoUnit.MILLIS.between(startInstant, Instant.now()), timeToPrepare);
		} catch (SQLException ex) {
			LOG.error("{}Database SQLException:{}", module, ex.getMessage());
		} finally {
			if (resultSet != null) {
				try {
					resultSet.close();
				} catch (SQLException ex) {	//ignore exception on close
				}
			}
		}
		return sb.toString();
	}
}


