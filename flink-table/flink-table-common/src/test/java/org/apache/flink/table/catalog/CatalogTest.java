/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.table.catalog;

import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.api.TableSchema;
import org.apache.flink.table.catalog.exceptions.CatalogException;
import org.apache.flink.table.catalog.exceptions.DatabaseAlreadyExistException;
import org.apache.flink.table.catalog.exceptions.DatabaseNotEmptyException;
import org.apache.flink.table.catalog.exceptions.DatabaseNotExistException;
import org.apache.flink.table.catalog.exceptions.FunctionAlreadyExistException;
import org.apache.flink.table.catalog.exceptions.FunctionNotExistException;
import org.apache.flink.table.catalog.exceptions.PartitionAlreadyExistsException;
import org.apache.flink.table.catalog.exceptions.PartitionNotExistException;
import org.apache.flink.table.catalog.exceptions.PartitionSpecInvalidException;
import org.apache.flink.table.catalog.exceptions.TableAlreadyExistException;
import org.apache.flink.table.catalog.exceptions.TableNotExistException;
import org.apache.flink.table.catalog.exceptions.TableNotPartitionedException;
import org.apache.flink.table.catalog.stats.CatalogColumnStatistics;
import org.apache.flink.table.functions.ScalarFunction;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Class for unit tests to run on catalogs.
 */
public abstract class CatalogTest {
	protected static final String IS_STREAMING = "is_streaming";

	protected final String db1 = "db1";
	protected final String db2 = "db2";

	protected final String t1 = "t1";
	protected final String t2 = "t2";
	protected final String t3 = "t3";
	protected final ObjectPath path1 = new ObjectPath(db1, t1);
	protected final ObjectPath path2 = new ObjectPath(db2, t2);
	protected final ObjectPath path3 = new ObjectPath(db1, t2);
	protected final ObjectPath path4 = new ObjectPath(db1, t3);
	protected final ObjectPath nonExistDbPath = ObjectPath.fromString("non.exist");
	protected final ObjectPath nonExistObjectPath = ObjectPath.fromString("db1.nonexist");

	public static final String TEST_CATALOG_NAME = "test-catalog";

	protected static final String TEST_COMMENT = "test comment";

	protected static Catalog catalog;

	@Rule
	public ExpectedException exception = ExpectedException.none();

	@After
	public void cleanup() throws Exception {
		if (catalog.tableExists(path1)) {
			catalog.dropTable(path1, true);
		}
		if (catalog.tableExists(path2)) {
			catalog.dropTable(path2, true);
		}
		if (catalog.tableExists(path3)) {
			catalog.dropTable(path3, true);
		}
		if (catalog.tableExists(path4)) {
			catalog.dropTable(path4, true);
		}
		if (catalog.functionExists(path1)) {
			catalog.dropFunction(path1, true);
		}
		if (catalog.databaseExists(db1)) {
			catalog.dropDatabase(db1, true);
		}
		if (catalog.databaseExists(db2)) {
			catalog.dropDatabase(db2, true);
		}
	}

	@AfterClass
	public static void closeup() {
		catalog.close();
	}

	// ------ databases ------

	@Test
	public void testCreateDb() throws Exception {
		assertFalse(catalog.databaseExists(db1));

		CatalogDatabase cd = createDb();
		catalog.createDatabase(db1, cd, false);

		assertTrue(catalog.databaseExists(db1));
		CatalogTestUtil.checkEquals(cd, catalog.getDatabase(db1));
	}

	@Test
	public void testCreateDb_DatabaseAlreadyExistException() throws Exception {
		catalog.createDatabase(db1, createDb(), false);

		exception.expect(DatabaseAlreadyExistException.class);
		exception.expectMessage("Database db1 already exists in Catalog");
		catalog.createDatabase(db1, createDb(), false);
	}

	@Test
	public void testCreateDb_DatabaseAlreadyExist_ignored() throws Exception {
		CatalogDatabase cd1 = createDb();
		catalog.createDatabase(db1, cd1, false);
		List<String> dbs = catalog.listDatabases();

		CatalogTestUtil.checkEquals(cd1, catalog.getDatabase(db1));
		assertEquals(2, dbs.size());
		assertEquals(new HashSet<>(Arrays.asList(db1, catalog.getDefaultDatabase())), new HashSet<>(dbs));

		catalog.createDatabase(db1, createAnotherDb(), true);

		CatalogTestUtil.checkEquals(cd1, catalog.getDatabase(db1));
		assertEquals(2, dbs.size());
		assertEquals(new HashSet<>(Arrays.asList(db1, catalog.getDefaultDatabase())), new HashSet<>(dbs));
	}

	@Test
	public void testGetDb_DatabaseNotExistException() throws Exception {
		exception.expect(DatabaseNotExistException.class);
		exception.expectMessage("Database nonexistent does not exist in Catalog");
		catalog.getDatabase("nonexistent");
	}

	@Test
	public void testDropDb() throws Exception {
		catalog.createDatabase(db1, createDb(), false);

		assertTrue(catalog.databaseExists(db1));

		catalog.dropDatabase(db1, false);

		assertFalse(catalog.databaseExists(db1));
	}

	@Test
	public void testDropDb_DatabaseNotExistException() throws Exception {
		exception.expect(DatabaseNotExistException.class);
		exception.expectMessage("Database db1 does not exist in Catalog");
		catalog.dropDatabase(db1, false);
	}

	@Test
	public void testDropDb_DatabaseNotExist_Ignore() throws Exception {
		catalog.dropDatabase(db1, true);
	}

	@Test
	public void testDropDb_DatabaseNotEmptyException() throws Exception {
		catalog.createDatabase(db1, createDb(), false);
		catalog.createTable(path1, createTable(), false);

		exception.expect(DatabaseNotEmptyException.class);
		exception.expectMessage("Database db1 in catalog test-catalog is not empty");
		catalog.dropDatabase(db1, true);
	}

	@Test
	public void testAlterDb() throws Exception {
		CatalogDatabase db = createDb();
		catalog.createDatabase(db1, db, false);

		CatalogDatabase newDb = createAnotherDb();
		catalog.alterDatabase(db1, newDb, false);

		assertFalse(catalog.getDatabase(db1).getProperties().entrySet().containsAll(db.getProperties().entrySet()));
		CatalogTestUtil.checkEquals(newDb, catalog.getDatabase(db1));
	}

	@Test
	public void testAlterDb_DatabaseNotExistException() throws Exception {
		exception.expect(DatabaseNotExistException.class);
		exception.expectMessage("Database nonexistent does not exist in Catalog");
		catalog.alterDatabase("nonexistent", createDb(), false);
	}

	@Test
	public void testAlterDb_DatabaseNotExist_ignored() throws Exception {
		catalog.alterDatabase("nonexistent", createDb(), true);

		assertFalse(catalog.databaseExists("nonexistent"));
	}

	@Test
	public void testDbExists() throws Exception {
		assertFalse(catalog.databaseExists("nonexistent"));

		catalog.createDatabase(db1, createDb(), false);

		assertTrue(catalog.databaseExists(db1));
	}

	// ------ tables ------

	@Test
	public void testCreateTable_Streaming() throws Exception {
		catalog.createDatabase(db1, createDb(), false);
		CatalogTable table = createStreamingTable();
		catalog.createTable(path1, table, false);

		checkEquals(table, (CatalogTable) catalog.getTable(path1));
	}

	@Test
	public void testCreateTable_Batch() throws Exception {
		catalog.createDatabase(db1, createDb(), false);

		// Non-partitioned table
		CatalogTable table = createTable();
		catalog.createTable(path1, table, false);

		CatalogBaseTable tableCreated = catalog.getTable(path1);

		checkEquals(table, (CatalogTable) tableCreated);
		assertEquals(TEST_COMMENT, tableCreated.getDescription().get());

		List<String> tables = catalog.listTables(db1);

		assertEquals(1, tables.size());
		assertEquals(path1.getObjectName(), tables.get(0));

		catalog.dropTable(path1, false);

		// Partitioned table
		table = createPartitionedTable();
		catalog.createTable(path1, table, false);

		checkEquals(table, (CatalogTable) catalog.getTable(path1));

		tables = catalog.listTables(db1);

		assertEquals(1, tables.size());
		assertEquals(path1.getObjectName(), tables.get(0));
	}

	@Test
	public void testCreateTable_DatabaseNotExistException() throws Exception {
		assertFalse(catalog.databaseExists(db1));

		exception.expect(DatabaseNotExistException.class);
		exception.expectMessage("Database db1 does not exist in Catalog");
		catalog.createTable(nonExistObjectPath, createTable(), false);
	}

	@Test
	public void testCreateTable_TableAlreadyExistException() throws Exception {
		catalog.createDatabase(db1, createDb(), false);
		catalog.createTable(path1,  createTable(), false);

		exception.expect(TableAlreadyExistException.class);
		exception.expectMessage("Table (or view) db1.t1 already exists in Catalog");
		catalog.createTable(path1, createTable(), false);
	}

	@Test
	public void testCreateTable_TableAlreadyExist_ignored() throws Exception {
		catalog.createDatabase(db1, createDb(), false);

		CatalogTable table = createTable();
		catalog.createTable(path1, table, false);

		checkEquals(table, (CatalogTable) catalog.getTable(path1));

		catalog.createTable(path1, createAnotherTable(), true);

		checkEquals(table, (CatalogTable) catalog.getTable(path1));
	}

	@Test
	public void testGetTable_TableNotExistException() throws Exception {
		catalog.createDatabase(db1, createDb(), false);

		exception.expect(TableNotExistException.class);
		exception.expectMessage("Table (or view) db1.nonexist does not exist in Catalog");
		catalog.getTable(nonExistObjectPath);
	}

	@Test
	public void testGetTable_TableNotExistException_NoDb() throws Exception {
		exception.expect(TableNotExistException.class);
		exception.expectMessage("Table (or view) db1.nonexist does not exist in Catalog");
		catalog.getTable(nonExistObjectPath);
	}

	@Test
	public void testDropTable_nonPartitionedTable() throws Exception {
		catalog.createDatabase(db1, createDb(), false);
		catalog.createTable(path1, createTable(), false);

		assertTrue(catalog.tableExists(path1));

		catalog.dropTable(path1, false);

		assertFalse(catalog.tableExists(path1));
	}

	@Test
	public void testDropTable_TableNotExistException() throws Exception {
		exception.expect(TableNotExistException.class);
		exception.expectMessage("Table (or view) non.exist does not exist in Catalog");
		catalog.dropTable(nonExistDbPath, false);
	}

	@Test
	public void testDropTable_TableNotExist_ignored() throws Exception {
		catalog.createDatabase(db1, createDb(), false);
		catalog.dropTable(nonExistObjectPath, true);
	}

	@Test
	public void testAlterTable() throws Exception {
		catalog.createDatabase(db1, createDb(), false);

		// Non-partitioned table
		CatalogTable table = createTable();
		catalog.createTable(path1, table, false);

		checkEquals(table, (CatalogTable) catalog.getTable(path1));

		CatalogTable newTable = createAnotherTable();
		catalog.alterTable(path1, newTable, false);

		assertNotEquals(table, catalog.getTable(path1));
		checkEquals(newTable, (CatalogTable) catalog.getTable(path1));

		catalog.dropTable(path1, false);

		// Partitioned table
		table = createPartitionedTable();
		catalog.createTable(path1, table, false);

		checkEquals(table, (CatalogTable) catalog.getTable(path1));

		newTable = createAnotherPartitionedTable();
		catalog.alterTable(path1, newTable, false);

		checkEquals(newTable, (CatalogTable) catalog.getTable(path1));

		// View
		CatalogView view = createView();
		catalog.createTable(path3, view, false);

		checkEquals(view, (CatalogView) catalog.getTable(path3));

		CatalogView newView = createAnotherView();
		catalog.alterTable(path3, newView, false);

		assertNotEquals(view, catalog.getTable(path3));
		checkEquals(newView, (CatalogView) catalog.getTable(path3));
	}

	@Test
	public void testAlterTable_differentTypedTable() throws Exception {
		catalog.createDatabase(db1, createDb(), false);

		CatalogTable table = createTable();
		catalog.createTable(path1, table, false);

		exception.expect(CatalogException.class);
		exception.expectMessage(
			String.format("Table types don't match. " +
					"Existing table is '%s' and " +
					"new table is 'org.apache.flink.table.catalog.CatalogTest$TestTable'.",
				table.getClass().getName()));
		catalog.alterTable(path1, new TestTable(), false);
	}

	@Test
	public void testAlterTable_TableNotExistException() throws Exception {
		exception.expect(TableNotExistException.class);
		exception.expectMessage("Table (or view) non.exist does not exist in Catalog");
		catalog.alterTable(nonExistDbPath, createTable(), false);
	}

	@Test
	public void testAlterTable_TableNotExist_ignored() throws Exception {
		catalog.createDatabase(db1, createDb(), false);
		catalog.alterTable(nonExistObjectPath, createTable(), true);

		assertFalse(catalog.tableExists(nonExistObjectPath));
	}

	@Test
	public void testRenameTable_nonPartitionedTable() throws Exception {
		catalog.createDatabase(db1, createDb(), false);
		CatalogTable table = createTable();
		catalog.createTable(path1, table, false);

		checkEquals(table, (CatalogTable) catalog.getTable(path1));

		catalog.renameTable(path1, t2, false);

		checkEquals(table, (CatalogTable) catalog.getTable(path3));
		assertFalse(catalog.tableExists(path1));
	}

	@Test
	public void testRenameTable_TableNotExistException() throws Exception {
		catalog.createDatabase(db1, createDb(), false);

		exception.expect(TableNotExistException.class);
		exception.expectMessage("Table (or view) db1.t1 does not exist in Catalog");
		catalog.renameTable(path1, t2, false);
	}

	@Test
	public void testRenameTable_TableNotExistException_ignored() throws Exception {
		catalog.createDatabase(db1, createDb(), false);
		catalog.renameTable(path1, t2, true);
	}

	@Test
	public void testRenameTable_TableAlreadyExistException() throws Exception {
		catalog.createDatabase(db1, createDb(), false);
		CatalogTable table = createTable();
		catalog.createTable(path1, table, false);
		catalog.createTable(path3, createAnotherTable(), false);

		exception.expect(TableAlreadyExistException.class);
		exception.expectMessage("Table (or view) db1.t2 already exists in Catalog");
		catalog.renameTable(path1, t2, false);
	}

	@Test
	public void testListTables() throws Exception {
		catalog.createDatabase(db1, createDb(), false);

		catalog.createTable(path1, createTable(), false);
		catalog.createTable(path3, createTable(), false);
		catalog.createTable(path4, createView(), false);

		assertEquals(3, catalog.listTables(db1).size());
		assertEquals(1, catalog.listViews(db1).size());
	}

	@Test
	public void testTableExists() throws Exception {
		catalog.createDatabase(db1, createDb(), false);

		assertFalse(catalog.tableExists(path1));

		catalog.createTable(path1, createTable(), false);

		assertTrue(catalog.tableExists(path1));
	}

	// ------ views ------

	@Test
	public void testCreateView() throws Exception {
		catalog.createDatabase(db1, createDb(), false);

		assertFalse(catalog.tableExists(path1));

		CatalogView view = createView();
		catalog.createTable(path1, view, false);

		assertTrue(catalog.getTable(path1) instanceof CatalogView);
		checkEquals(view, (CatalogView) catalog.getTable(path1));
	}

	@Test
	public void testCreateView_DatabaseNotExistException() throws Exception {
		assertFalse(catalog.databaseExists(db1));

		exception.expect(DatabaseNotExistException.class);
		exception.expectMessage("Database db1 does not exist in Catalog");
		catalog.createTable(nonExistObjectPath, createView(), false);
	}

	@Test
	public void testCreateView_TableAlreadyExistException() throws Exception {
		catalog.createDatabase(db1, createDb(), false);
		catalog.createTable(path1, createView(), false);

		exception.expect(TableAlreadyExistException.class);
		exception.expectMessage("Table (or view) db1.t1 already exists in Catalog");
		catalog.createTable(path1, createView(), false);
	}

	@Test
	public void testCreateView_TableAlreadyExist_ignored() throws Exception {
		catalog.createDatabase(db1, createDb(), false);

		CatalogView view = createView();
		catalog.createTable(path1, view, false);

		assertTrue(catalog.getTable(path1) instanceof CatalogView);
		checkEquals(view, (CatalogView) catalog.getTable(path1));

		catalog.createTable(path1, createAnotherView(), true);

		assertTrue(catalog.getTable(path1) instanceof CatalogView);
		checkEquals(view, (CatalogView) catalog.getTable(path1));
	}

	@Test
	public void testDropView() throws Exception {
		catalog.createDatabase(db1, createDb(), false);
		catalog.createTable(path1, createView(), false);

		assertTrue(catalog.tableExists(path1));

		catalog.dropTable(path1, false);

		assertFalse(catalog.tableExists(path1));
	}

	@Test
	public void testAlterView() throws Exception {
		catalog.createDatabase(db1, createDb(), false);

		CatalogView view = createView();
		catalog.createTable(path1, view, false);

		checkEquals(view, (CatalogView) catalog.getTable(path1));

		CatalogView newView = createAnotherView();
		catalog.alterTable(path1, newView, false);

		assertTrue(catalog.getTable(path1) instanceof CatalogView);
		checkEquals(newView, (CatalogView) catalog.getTable(path1));
	}

	@Test
	public void testAlterView_TableNotExistException() throws Exception {
		exception.expect(TableNotExistException.class);
		exception.expectMessage("Table (or view) non.exist does not exist in Catalog");
		catalog.alterTable(nonExistDbPath, createTable(), false);
	}

	@Test
	public void testAlterView_TableNotExist_ignored() throws Exception {
		catalog.createDatabase(db1, createDb(), false);
		catalog.alterTable(nonExistObjectPath, createView(), true);

		assertFalse(catalog.tableExists(nonExistObjectPath));
	}

	@Test
	public void testListView() throws Exception {
		catalog.createDatabase(db1, createDb(), false);

		assertTrue(catalog.listTables(db1).isEmpty());

		catalog.createTable(path1, createView(), false);
		catalog.createTable(path3, createTable(), false);

		assertEquals(2, catalog.listTables(db1).size());
		assertEquals(new HashSet<>(Arrays.asList(path1.getObjectName(), path3.getObjectName())),
			new HashSet<>(catalog.listTables(db1)));
		assertEquals(Arrays.asList(path1.getObjectName()), catalog.listViews(db1));
	}

	@Test
	public void testRenameView() throws Exception {
		catalog.createDatabase(db1, createDb(), false);
		catalog.createTable(path1, createView(), false);

		assertTrue(catalog.tableExists(path1));

		catalog.renameTable(path1, t2, false);

		assertFalse(catalog.tableExists(path1));
		assertTrue(catalog.tableExists(path3));
	}

	// ------ functions ------

	@Test
	public void testCreateFunction() throws Exception {
		catalog.createDatabase(db1, createDb(), false);

		assertFalse(catalog.functionExists(path1));

		catalog.createFunction(path1, createFunction(), false);

		assertTrue(catalog.functionExists(path1));
	}

	@Test
	public void testCreateFunction_DatabaseNotExistException() throws Exception {
		assertFalse(catalog.databaseExists(db1));

		exception.expect(DatabaseNotExistException.class);
		exception.expectMessage("Database db1 does not exist in Catalog");
		catalog.createFunction(path1, createFunction(), false);
	}

	@Test
	public void testCreateFunction_FunctionAlreadyExistException() throws Exception {
		catalog.createDatabase(db1, createDb(), false);
		catalog.createFunction(path1, createFunction(), false);

		assertTrue(catalog.functionExists(path1));

		// test 'ignoreIfExist' flag
		catalog.createFunction(path1, createAnotherFunction(), true);

		exception.expect(FunctionAlreadyExistException.class);
		exception.expectMessage("Function db1.t1 already exists in Catalog");
		catalog.createFunction(path1, createFunction(), false);
	}

	@Test
	public void testAlterFunction() throws Exception {
		catalog.createDatabase(db1, createDb(), false);

		CatalogFunction func = createFunction();
		catalog.createFunction(path1, func, false);

		checkEquals(func, catalog.getFunction(path1));

		CatalogFunction newFunc = createAnotherFunction();
		catalog.alterFunction(path1, newFunc, false);
		CatalogFunction actual = catalog.getFunction(path1);

		assertFalse(func.getClassName().equals(actual.getClassName()));
		checkEquals(newFunc, actual);
	}

	@Test
	public void testAlterFunction_differentTypedFunction() throws Exception {
		catalog.createDatabase(db1, createDb(), false);
		CatalogFunction function = createFunction();
		catalog.createFunction(path1, createFunction(), false);

		exception.expect(CatalogException.class);
		exception.expectMessage(
			String.format("Function types don't match. " +
				"Existing function is '%s' and " +
				"new function is 'org.apache.flink.table.catalog.CatalogTest$TestFunction'.",
				function.getClass().getName()));
		catalog.alterFunction(path1, new TestFunction(), false);
	}

	@Test
	public void testAlterFunction_FunctionNotExistException() throws Exception {
		exception.expect(FunctionNotExistException.class);
		exception.expectMessage("Function db1.nonexist does not exist in Catalog");
		catalog.alterFunction(nonExistObjectPath, createFunction(), false);
	}

	@Test
	public void testAlterFunction_FunctionNotExist_ignored() throws Exception {
		catalog.createDatabase(db1, createDb(), false);
		catalog.alterFunction(nonExistObjectPath, createFunction(), true);

		assertFalse(catalog.functionExists(nonExistObjectPath));
	}

	@Test
	public void testListFunctions() throws Exception {
		catalog.createDatabase(db1, createDb(), false);

		CatalogFunction func = createFunction();
		catalog.createFunction(path1, func, false);

		assertEquals(path1.getObjectName(), catalog.listFunctions(db1).get(0));
	}

	@Test
	public void testListFunctions_DatabaseNotExistException() throws Exception{
		exception.expect(DatabaseNotExistException.class);
		exception.expectMessage("Database db1 does not exist in Catalog");
		catalog.listFunctions(db1);
	}

	@Test
	public void testGetFunction_FunctionNotExistException() throws Exception {
		catalog.createDatabase(db1, createDb(), false);

		exception.expect(FunctionNotExistException.class);
		exception.expectMessage("Function db1.nonexist does not exist in Catalog");
		catalog.getFunction(nonExistObjectPath);
	}

	@Test
	public void testGetFunction_FunctionNotExistException_NoDb() throws Exception {
		exception.expect(FunctionNotExistException.class);
		exception.expectMessage("Function db1.nonexist does not exist in Catalog");
		catalog.getFunction(nonExistObjectPath);
	}

	@Test
	public void testDropFunction() throws Exception {
		catalog.createDatabase(db1, createDb(), false);
		catalog.createFunction(path1, createFunction(), false);

		assertTrue(catalog.functionExists(path1));

		catalog.dropFunction(path1, false);

		assertFalse(catalog.functionExists(path1));
	}

	@Test
	public void testDropFunction_FunctionNotExistException() throws Exception {
		exception.expect(FunctionNotExistException.class);
		exception.expectMessage("Function non.exist does not exist in Catalog");
		catalog.dropFunction(nonExistDbPath, false);
	}

	@Test
	public void testDropFunction_FunctionNotExist_ignored() throws Exception {
		catalog.createDatabase(db1, createDb(), false);
		catalog.dropFunction(nonExistObjectPath, true);
		catalog.dropDatabase(db1, false);
	}

	// ------ partitions ------

	@Test
	public void testCreatePartition() throws Exception {
		catalog.createDatabase(db1, createDb(), false);
		catalog.createTable(path1, createPartitionedTable(), false);

		assertTrue(catalog.listPartitions(path1).isEmpty());

		catalog.createPartition(path1, createPartitionSpec(), createPartition(), false);

		assertEquals(Collections.singletonList(createPartitionSpec()), catalog.listPartitions(path1));
		assertEquals(Collections.singletonList(createPartitionSpec()), catalog.listPartitions(path1, createPartitionSpecSubset()));
		checkEquals(createPartition(), catalog.getPartition(path1, createPartitionSpec()));

		catalog.createPartition(path1, createAnotherPartitionSpec(), createPartition(), false);

		assertEquals(Arrays.asList(createPartitionSpec(), createAnotherPartitionSpec()), catalog.listPartitions(path1));
		assertEquals(Arrays.asList(createPartitionSpec(), createAnotherPartitionSpec()), catalog.listPartitions(path1, createPartitionSpecSubset()));
		checkEquals(createPartition(), catalog.getPartition(path1, createAnotherPartitionSpec()));
	}

	@Test
	public void testCreatePartition_TableNotExistException() throws Exception {
		catalog.createDatabase(db1, createDb(), false);

		exception.expect(TableNotExistException.class);
		exception.expectMessage(
			String.format("Table (or view) %s does not exist in Catalog %s.", path1.getFullName(), TEST_CATALOG_NAME));
		catalog.createPartition(path1, createPartitionSpec(), createPartition(), false);
	}

	@Test
	public void testCreatePartition_TableNotPartitionedException() throws Exception {
		catalog.createDatabase(db1, createDb(), false);
		catalog.createTable(path1, createTable(), false);

		exception.expect(TableNotPartitionedException.class);
		exception.expectMessage(
			String.format("Table %s in catalog %s is not partitioned.", path1.getFullName(), TEST_CATALOG_NAME));
		catalog.createPartition(path1, createPartitionSpec(), createPartition(), false);
	}

	@Test
	public void testCreatePartition_PartitionSpecInvalidException() throws Exception {
		catalog.createDatabase(db1, createDb(), false);
		CatalogTable table = createPartitionedTable();
		catalog.createTable(path1, table, false);

		CatalogPartitionSpec partitionSpec = createInvalidPartitionSpecSubset();
		exception.expect(PartitionSpecInvalidException.class);
		exception.expectMessage(
			String.format("PartitionSpec %s does not match partition keys %s of table %s in catalog %s.",
				partitionSpec, table.getPartitionKeys(), path1.getFullName(), TEST_CATALOG_NAME));
		catalog.createPartition(path1, partitionSpec, createPartition(), false);
	}

	@Test
	public void testCreatePartition_PartitionAlreadyExistsException() throws Exception {
		catalog.createDatabase(db1, createDb(), false);
		catalog.createTable(path1, createPartitionedTable(), false);
		CatalogPartition partition = createPartition();
		catalog.createPartition(path1, createPartitionSpec(), partition, false);

		CatalogPartitionSpec partitionSpec = createPartitionSpec();

		exception.expect(PartitionAlreadyExistsException.class);
		exception.expectMessage(
			String.format("Partition %s of table %s in catalog %s already exists.",
				partitionSpec, path1.getFullName(), TEST_CATALOG_NAME));
		catalog.createPartition(path1, partitionSpec, createPartition(), false);
	}

	@Test
	public void testCreatePartition_PartitionAlreadyExists_ignored() throws Exception {
		catalog.createDatabase(db1, createDb(), false);
		catalog.createTable(path1, createPartitionedTable(), false);

		CatalogPartitionSpec partitionSpec = createPartitionSpec();
		catalog.createPartition(path1, partitionSpec, createPartition(), false);
		catalog.createPartition(path1, partitionSpec, createPartition(), true);
	}

	@Test
	public void testDropPartition() throws Exception {
		catalog.createDatabase(db1, createDb(), false);
		catalog.createTable(path1, createPartitionedTable(), false);
		catalog.createPartition(path1, createPartitionSpec(), createPartition(), false);

		assertEquals(Collections.singletonList(createPartitionSpec()), catalog.listPartitions(path1));

		catalog.dropPartition(path1, createPartitionSpec(), false);

		assertEquals(Collections.emptyList(), catalog.listPartitions(path1));
	}

	@Test
	public void testDropPartition_TableNotExist() throws Exception {
		catalog.createDatabase(db1, createDb(), false);

		CatalogPartitionSpec partitionSpec = createPartitionSpec();
		exception.expect(PartitionNotExistException.class);
		exception.expectMessage(
			String.format("Partition %s of table %s in catalog %s does not exist.",
				partitionSpec, path1.getFullName(), TEST_CATALOG_NAME));
		catalog.dropPartition(path1, partitionSpec, false);
	}

	@Test
	public void testDropPartition_TableNotPartitioned() throws Exception {
		catalog.createDatabase(db1, createDb(), false);
		catalog.createTable(path1, createTable(), false);

		CatalogPartitionSpec partitionSpec = createPartitionSpec();
		exception.expect(PartitionNotExistException.class);
		exception.expectMessage(
			String.format("Partition %s of table %s in catalog %s does not exist.",
				partitionSpec, path1.getFullName(), TEST_CATALOG_NAME));
		catalog.dropPartition(path1, partitionSpec, false);
	}

	@Test
	public void testDropPartition_PartitionSpecInvalid() throws Exception {
		catalog.createDatabase(db1, createDb(), false);
		CatalogTable table = createPartitionedTable();
		catalog.createTable(path1, table, false);

		CatalogPartitionSpec partitionSpec = createInvalidPartitionSpecSubset();
		exception.expect(PartitionNotExistException.class);
		exception.expectMessage(
			String.format("Partition %s of table %s in catalog %s does not exist.",
				partitionSpec, path1.getFullName(), TEST_CATALOG_NAME));
		catalog.dropPartition(path1, partitionSpec, false);
	}

	@Test
	public void testDropPartition_PartitionNotExist() throws Exception {
		catalog.createDatabase(db1, createDb(), false);
		catalog.createTable(path1, createPartitionedTable(), false);

		CatalogPartitionSpec partitionSpec = createPartitionSpec();
		exception.expect(PartitionNotExistException.class);
		exception.expectMessage(
			String.format("Partition %s of table %s in catalog %s does not exist.", partitionSpec, path1.getFullName(), TEST_CATALOG_NAME));
		catalog.dropPartition(path1, partitionSpec, false);
	}

	@Test
	public void testDropPartition_PartitionNotExist_ignored() throws Exception {
		catalog.createDatabase(db1, createDb(), false);
		catalog.createTable(path1, createPartitionedTable(), false);
		catalog.dropPartition(path1, createPartitionSpec(), true);
	}

	@Test
	public void testAlterPartition() throws Exception {
		catalog.createDatabase(db1, createDb(), false);
		catalog.createTable(path1, createPartitionedTable(), false);
		catalog.createPartition(path1, createPartitionSpec(), createPartition(), false);

		assertEquals(Collections.singletonList(createPartitionSpec()), catalog.listPartitions(path1));
		CatalogPartition cp = catalog.getPartition(path1, createPartitionSpec());
		checkEquals(createPartition(), cp);
		assertNull(cp.getProperties().get("k"));

		CatalogPartition another = createPartition();
		another.getProperties().put("k", "v");
		catalog.alterPartition(path1, createPartitionSpec(), another, false);

		assertEquals(Collections.singletonList(createPartitionSpec()), catalog.listPartitions(path1));
		cp = catalog.getPartition(path1, createPartitionSpec());
		checkEquals(another, cp);
		assertEquals("v", cp.getProperties().get("k"));
	}

	@Test
	public void testAlterPartition_TableNotExist() throws Exception {
		catalog.createDatabase(db1, createDb(), false);

		CatalogPartitionSpec partitionSpec = createPartitionSpec();
		exception.expect(PartitionNotExistException.class);
		exception.expectMessage(
			String.format("Partition %s of table %s in catalog %s does not exist.",
				partitionSpec, path1.getFullName(), TEST_CATALOG_NAME));
		catalog.alterPartition(path1, partitionSpec, createPartition(), false);
	}

	@Test
	public void testAlterPartition_TableNotPartitioned() throws Exception {
		catalog.createDatabase(db1, createDb(), false);
		catalog.createTable(path1, createTable(), false);

		CatalogPartitionSpec partitionSpec = createPartitionSpec();
		exception.expect(PartitionNotExistException.class);
		exception.expectMessage(
			String.format("Partition %s of table %s in catalog %s does not exist.",
				partitionSpec, path1.getFullName(), TEST_CATALOG_NAME));
		catalog.alterPartition(path1, partitionSpec, createPartition(), false);
	}

	@Test
	public void testAlterPartition_PartitionSpecInvalid() throws Exception {
		catalog.createDatabase(db1, createDb(), false);
		CatalogTable table = createPartitionedTable();
		catalog.createTable(path1, table, false);

		CatalogPartitionSpec partitionSpec = createInvalidPartitionSpecSubset();
		exception.expect(PartitionNotExistException.class);
		exception.expectMessage(
			String.format("Partition %s of table %s in catalog %s does not exist.",
				partitionSpec, path1.getFullName(), TEST_CATALOG_NAME));
		catalog.alterPartition(path1, partitionSpec, createPartition(), false);
	}

	@Test
	public void testAlterPartition_PartitionNotExist() throws Exception {
		catalog.createDatabase(db1, createDb(), false);
		catalog.createTable(path1, createPartitionedTable(), false);

		CatalogPartitionSpec partitionSpec = createPartitionSpec();
		exception.expect(PartitionNotExistException.class);
		exception.expectMessage(
			String.format("Partition %s of table %s in catalog %s does not exist.",
				partitionSpec, path1.getFullName(), TEST_CATALOG_NAME));
		catalog.alterPartition(path1, partitionSpec, createPartition(), false);
	}

	@Test
	public void testAlterPartition_PartitionNotExist_ignored() throws Exception {
		catalog.createDatabase(db1, createDb(), false);
		catalog.createTable(path1, createPartitionedTable(), false);
		catalog.alterPartition(path1, createPartitionSpec(), createPartition(), true);
	}

	@Test
	public void testGetPartition_TableNotExist() throws Exception {
		CatalogPartitionSpec partitionSpec = createPartitionSpec();
		exception.expect(PartitionNotExistException.class);
		exception.expectMessage(
			String.format("Partition %s of table %s in catalog %s does not exist.", partitionSpec,
				path1.getFullName(), TEST_CATALOG_NAME));
		catalog.getPartition(path1, partitionSpec);
	}

	@Test
	public void testGetPartition_TableNotPartitioned() throws Exception {
		catalog.createDatabase(db1, createDb(), false);
		catalog.createTable(path1, createTable(), false);

		CatalogPartitionSpec partitionSpec = createPartitionSpec();
		exception.expect(PartitionNotExistException.class);
		exception.expectMessage(
			String.format("Partition %s of table %s in catalog %s does not exist.", partitionSpec,
				path1.getFullName(), TEST_CATALOG_NAME));
		catalog.getPartition(path1, partitionSpec);
	}

	@Test
	public void testGetPartition_PartitionSpecInvalid_invalidPartitionSpec() throws Exception {
		catalog.createDatabase(db1, createDb(), false);
		CatalogTable table = createPartitionedTable();
		catalog.createTable(path1, table, false);

		CatalogPartitionSpec partitionSpec = createInvalidPartitionSpecSubset();
		exception.expect(PartitionNotExistException.class);
		exception.expectMessage(
			String.format("Partition %s of table %s in catalog %s does not exist.",
				partitionSpec, path1.getFullName(), TEST_CATALOG_NAME));
		catalog.getPartition(path1, partitionSpec);
	}

	@Test
	public void testGetPartition_PartitionSpecInvalid_sizeNotEqual() throws Exception {
		catalog.createDatabase(db1, createDb(), false);
		CatalogTable table = createPartitionedTable();
		catalog.createTable(path1, table, false);

		CatalogPartitionSpec partitionSpec = new CatalogPartitionSpec(
			new HashMap<String, String>() {{
				put("second", "bob");
			}}
		);
		exception.expect(PartitionNotExistException.class);
		exception.expectMessage(
			String.format("Partition %s of table %s in catalog %s does not exist.",
				partitionSpec, path1.getFullName(), TEST_CATALOG_NAME));
		catalog.getPartition(path1, partitionSpec);
	}

	@Test
	public void testGetPartition_PartitionNotExist() throws Exception {
		catalog.createDatabase(db1, createDb(), false);
		catalog.createTable(path1, createPartitionedTable(), false);

		CatalogPartitionSpec partitionSpec = createPartitionSpec();
		exception.expect(PartitionNotExistException.class);
		exception.expectMessage(
			String.format("Partition %s of table %s in catalog %s does not exist.",
				partitionSpec, path1.getFullName(), TEST_CATALOG_NAME));
		catalog.getPartition(path1, partitionSpec);
	}

	@Test
	public void testPartitionExists() throws Exception {
		catalog.createDatabase(db1, createDb(), false);
		catalog.createTable(path1, createPartitionedTable(), false);
		catalog.createPartition(path1, createPartitionSpec(), createPartition(), false);

		assertTrue(catalog.partitionExists(path1, createPartitionSpec()));
		assertFalse(catalog.partitionExists(path2, createPartitionSpec()));
		assertFalse(catalog.partitionExists(ObjectPath.fromString("non.exist"), createPartitionSpec()));
	}

	@Test
	public void testListPartitionPartialSpec() throws Exception {
		catalog.createDatabase(db1, createDb(), false);
		catalog.createTable(path1, createPartitionedTable(), false);
		catalog.createPartition(path1, createPartitionSpec(), createPartition(), false);
		catalog.createPartition(path1, createAnotherPartitionSpec(), createPartition(), false);

		assertEquals(2, catalog.listPartitions(path1, createPartitionSpecSubset()).size());
		assertEquals(1, catalog.listPartitions(path1, createAnotherPartitionSpecSubset()).size());
	}

	// ------ utilities ------

	/**
	 * Create a CatalogDatabase instance by specific catalog implementation.
	 *
	 * @return a CatalogDatabase instance
	 */
	public abstract CatalogDatabase createDb();

	/**
	 * Create another CatalogDatabase instance by specific catalog implementation.
	 *
	 * @return another CatalogDatabase instance
	 */
	public abstract CatalogDatabase createAnotherDb();

	/**
	 * Create a CatalogTable instance by specific catalog implementation.
	 *
	 * @return a CatalogTable instance
	 */
	public abstract CatalogTable createTable();

	/**
	 * Create another CatalogTable instance by specific catalog implementation.
	 *
	 * @return another CatalogTable instance
	 */
	public abstract CatalogTable createAnotherTable();

	/**
	 * Create a streaming CatalogTable instance by specific catalog implementation.
	 *
	 * @return a streaming CatalogTable instance
	 */
	public abstract CatalogTable createStreamingTable();

	/**
	 * Create a partitioned CatalogTable instance by specific catalog implementation.
	 *
	 * @return a streaming CatalogTable instance
	 */
	public abstract CatalogTable createPartitionedTable();

	/**
	 * Create another partitioned CatalogTable instance by specific catalog implementation.
	 *
	 * @return another partitioned CatalogTable instance
	 */
	public abstract CatalogTable createAnotherPartitionedTable();

	/**
	 * Create a CatalogView instance by specific catalog implementation.
	 *
	 * @return a CatalogView instance
	 */
	public abstract CatalogView createView();

	/**
	 * Create another CatalogView instance by specific catalog implementation.
	 *
	 * @return another CatalogView instance
	 */
	public abstract CatalogView createAnotherView();

	/**
	 * Create a CatalogFunction instance by specific catalog implementation.
	 *
	 * @return a CatalogFunction instance
	 */
	protected abstract CatalogFunction createFunction();

	/**
	 * Create another CatalogFunction instance by specific catalog implementation.
	 *
	 * @return another CatalogFunction instance
	 */
	protected abstract CatalogFunction createAnotherFunction();

	/**
	 * Creates a CatalogPartition by specific catalog implementation.
	 *
	 * @return a CatalogPartition
	 */
	public abstract CatalogPartition createPartition();

	protected TableSchema createTableSchema() {
		return TableSchema.builder()
			.field("first", DataTypes.STRING())
			.field("second", DataTypes.INT())
			.field("third", DataTypes.STRING())
			.build();
	}

	protected TableSchema createAnotherTableSchema() {
		return TableSchema.builder()
			.field("first", DataTypes.STRING())
			.field("second", DataTypes.STRING())
			.field("third", DataTypes.STRING())
			.build();
	}

	protected List<String> createPartitionKeys() {
		return Arrays.asList("second", "third");
	}

	protected CatalogPartitionSpec createPartitionSpec() {
		return new CatalogPartitionSpec(
			new HashMap<String, String>() {{
				put("third", "2000");
				put("second", "bob");
			}});
	}

	protected CatalogPartitionSpec createAnotherPartitionSpec() {
		return new CatalogPartitionSpec(
			new HashMap<String, String>() {{
				put("third", "2010");
				put("second", "bob");
			}});
	}

	protected CatalogPartitionSpec createPartitionSpecSubset() {
		return new CatalogPartitionSpec(
			new HashMap<String, String>() {{
				put("second", "bob");
			}});
	}

	protected CatalogPartitionSpec createAnotherPartitionSpecSubset() {
		return new CatalogPartitionSpec(
			new HashMap<String, String>() {{
				put("third", "2000");
			}}
		);
	}

	protected CatalogPartitionSpec createInvalidPartitionSpecSubset() {
		return new CatalogPartitionSpec(
			new HashMap<String, String>() {{
				put("third", "2010");
			}});
	}

	protected Map<String, String> getBatchTableProperties() {
		return new HashMap<String, String>() {{
			put(IS_STREAMING, "false");
		}};
	}

	protected Map<String, String> getStreamingTableProperties() {
		return new HashMap<String, String>() {{
			put(IS_STREAMING, "true");
		}};
	}

	/**
	 * A Flink function for test.
	 */
	public static class MyScalarFunction extends ScalarFunction {
		public Integer eval(Integer i) {
			return i + 1;
		}
	}

	/**
	 * Another Flink function for test.
	 */
	public static class MyOtherScalarFunction extends ScalarFunction {
		public String eval(Integer i) {
			return String.valueOf(i);
		}
	}

	/**
	 * Test table used to assert on table of different class.
	 */
	public static class TestTable implements CatalogBaseTable {

		@Override
		public Map<String, String> getProperties() {
			return null;
		}

		@Override
		public TableSchema getSchema() {
			return null;
		}

		@Override
		public String getComment() {
			return null;
		}

		@Override
		public CatalogBaseTable copy() {
			return null;
		}

		@Override
		public Optional<String> getDescription() {
			return Optional.empty();
		}

		@Override
		public Optional<String> getDetailedDescription() {
			return Optional.empty();
		}
	}

	/**
	 * Test partition used to assert on partition of different class.
	 */
	public static class TestPartition implements CatalogPartition {
		@Override
		public Map<String, String> getProperties() {
			return null;
		}

		@Override
		public CatalogPartition copy() {
			return null;
		}

		@Override
		public Optional<String> getDescription() {
			return Optional.empty();
		}

		@Override
		public Optional<String> getDetailedDescription() {
			return Optional.empty();
		}
	}

	/**
	 * Test function used to assert on function of different class.
	 */
	public static class TestFunction implements CatalogFunction {
		@Override
		public String getClassName() {
			return null;
		}

		@Override
		public Map<String, String> getProperties() {
			return null;
		}

		@Override
		public CatalogFunction copy() {
			return null;
		}

		@Override
		public Optional<String> getDescription() {
			return Optional.empty();
		}

		@Override
		public Optional<String> getDetailedDescription() {
			return Optional.empty();
		}
	}

	// ------ equality check utils ------
	// Can be overriden by sub test class

	protected void checkEquals(CatalogTable t1, CatalogTable t2) {
		assertEquals(t1.getSchema(), t2.getSchema());
		assertEquals(t1.getProperties(), t2.getProperties());
		assertEquals(t1.getComment(), t2.getComment());
		assertEquals(t1.getPartitionKeys(), t2.getPartitionKeys());
		assertEquals(t1.isPartitioned(), t2.isPartitioned());
	}

	protected void checkEquals(CatalogView v1, CatalogView v2) {
		assertEquals(v1.getSchema(), v1.getSchema());
		assertEquals(v1.getProperties(), v2.getProperties());
		assertEquals(v1.getComment(), v2.getComment());
		assertEquals(v1.getOriginalQuery(), v2.getOriginalQuery());
		assertEquals(v1.getExpandedQuery(), v2.getExpandedQuery());
	}

	protected void checkEquals(CatalogFunction f1, CatalogFunction f2) {
		assertEquals(f1.getClassName(), f2.getClassName());
		assertEquals(f1.getProperties(), f2.getProperties());
	}

	protected void checkEquals(CatalogPartition expected, CatalogPartition actual) {
		assertEquals(expected.getProperties(), actual.getProperties());
	}

	protected void checkEquals(CatalogColumnStatistics cs1, CatalogColumnStatistics cs2) {
		CatalogTestUtil.checkEquals(cs1, cs2);
	}
}
