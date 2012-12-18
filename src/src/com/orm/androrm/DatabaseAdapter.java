/**
 * Copyright (C) 2012 Clyde Stubbs,   2010 Philipp Giese
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.orm.androrm;

import java.util.Collection;
import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This class provides access to the underlying SQLite database.
 *
 * @author Philipp Giese
 */
public class DatabaseAdapter extends SQLiteOpenHelper {

	private static final String TAG = "ANDRORM:DatabaseAdapter";
	/**
	 * {@link android.database.sqlite.SQLiteDatabase SQLite database} to store the data.
	 */
	private SQLiteDatabase mDb;
	private QueryBuilder queryBuilder;
	private ModelCache modelCache;
	/**
	 * {@link Set} containing names of all tables, that were created by this class.
	 */
	private Set<String> mTables = new HashSet<String>();
	/**
	 * {@link Set} containing all classes, that are handled by the ORM.
	 */
	private Set<Class<? extends Model>> mModels = new HashSet<Class<? extends Model>>();

	public DatabaseAdapter(String name, Context context, int version) {
		super(context, name, null, version);
		queryBuilder = new QueryBuilder(this);
		modelCache = new ModelCache();
	}

	public QueryBuilder getQueryBuilder() {
		return queryBuilder;
	}

	/**
	 * Closes the current connection to the database. Call this method after every database
	 * interaction to prevent data leaks.
	 */
	public void close() {
		super.close();
		unLock();
	}

	/**
	 * Delete one object or a set of objects from a specific table.
	 *
	 * @param table Query table.
	 * @param where {@link Where} clause to find the object.
	 * @return Number of affected rows.
	 */
	public int delete(String table, Where where) {
		open();
		int affectedRows = mDb.delete(table, where.toString().replace(" WHERE ", ""), null);
		close();

		return affectedRows;
	}

	/**
	 * Inserts values into a table that has an unique id as identifier.
	 *
	 * @param table  The affected table.
	 * @param values The values to be inserted/ updated.
	 * @param where  A constraint for the operation.
	 * @return The number of rows affected on update, the rowId on insert, -1 on error.
	 */
	public int doInsertOrUpdate(String table, ContentValues values, Where where) {
		int result;

		open();
		Cursor oldVersion = get(table, where, null);

		boolean b = oldVersion.moveToNext();
		oldVersion.close();
		if(b) {
			String whereClause = null;
			if(where != null)
				whereClause = where.toString().replace(" WHERE ", "");

			result = mDb.update(table, values, whereClause, null);
		} else {
			String nullColumnHack = null;

			if(values.size() == 0)
				// if no fields are defined on a model instance the nullColumnHack
				// needs to be utilized in order to insert an empty row. 
				nullColumnHack = Model.PK;

			result = (int) mDb.insertOrThrow(table, nullColumnHack, values);
		}

		close();
		return result;
	}

	/**
	 * Drops all tables of the current database.
	 */
	public void drop() {
		open();
		drop(mDb);
		onCreate(mDb);
		close();
	}

	/**
	 * Drops a specific table
	 *
	 * @param tableName Name of the table to drop.
	 */
	public void drop(String tableName) {
		open();
		String sql = "DROP TABLE IF EXISTS `" + tableName + "`;";
		mDb.execSQL(sql);
		onCreate(mDb);
		close();
	}

	/**
	 * Query the database for a specific item.
	 *
	 * @param table Query table.
	 * @param where {@link Where} clause to apply.
	 * @param limit {@link Limit} clause to apply.
	 * @return {@link Cursor} that represents the query result.
	 */
	private Cursor get(String table, Where where, Limit limit) {
		String whereClause = null;
		if(where != null)
			whereClause = where.toString().replace(" WHERE ", "");

		String limitClause = null;
		if(limit != null)
			limitClause = limit.toString().replace(" LIMIT ", "");

		Cursor result = mDb.query(table,
				null,
				whereClause,
				null,
				null,
				null,
				null,
				limitClause);

		return result;
	}

	/**
	 * This opens a new database connection. If a connection or database already exists the system
	 * will ensure that getWritableDatabase() will return this Database.
	 * <p/>
	 * DO NOT try to do caching by yourself because this could result in an inappropriate state of
	 * the database.
	 *
	 * @return this to enable chaining.
	 * @throws SQLException
	 */
	public DatabaseAdapter open() throws SQLException {
		lock();
		mDb = getWritableDatabase();

		return this;
	}

	public Cursor query(SelectStatement select) {
		return mDb.rawQuery(select.toString(), null);
	}

	public Cursor query(String query) {
		return mDb.rawQuery(query, null);
	}

	/**
	 * Registers all models, that will then be handled by the ORM.
	 *
	 * @param models {@link List} of classes inheriting from {@link Model}.
	 */
	public void setModels(Collection<Class<? extends Model>> models) {
		open();
		mModels = new HashSet<Class<? extends Model>>();
		mModels.addAll(models);
		onCreate(mDb);
		close();
	}

	public String getTableName(Class<?> clazz) {
		return clazz.getSimpleName().toLowerCase();
	}

	public void reset() {
		modelCache.reset();
	}

	protected final <T extends Model> List<TableDefinition> getTableDefinitions(Class<T> clazz) {
		List<TableDefinition> definitions = new ArrayList<TableDefinition>();

		if(!Modifier.isAbstract(clazz.getModifiers()))
			try {
				if(modelCache.knowsModel(clazz))
					return modelCache.getTableDefinitions(clazz);

				T object = getInstance(clazz);
				TableDefinition definition = new TableDefinition(getTableName(clazz));

				getFieldDefinitions(object, clazz, definition);

				definitions.add(definition);

				for(Class<? extends Model> c : definition.getRelationalClasses())
					definitions.addAll(getRelationDefinitions(c));

				modelCache.setTableDefinitions(clazz, definitions);

				return definitions;
			} catch(IllegalAccessException e) {
				Log.e(TAG, "an exception has been thrown while gathering the database structure information.", e);
			}

		return null;
	}

	private final <T extends Model> void getFieldDefinitions(
			T instance,
			Class<T> clazz,
			TableDefinition modelTable) throws IllegalArgumentException, IllegalAccessException {

		if(clazz != null && clazz.isInstance(instance)) {
			// TODO: only create fields from superclass, if superclass is
			// abstract. Otherwise create a pointer to superclass.

			modelCache.addModel(clazz);

			for(Field field : getFields(clazz, instance)) {
				String name = field.getName();

				Object o = field.get(instance);

				if(o instanceof DataField) {
					DataField<?> fieldObject = (DataField<?>) o;
					modelTable.addField(name, fieldObject);
				}

				if(o instanceof ManyToManyField)
					modelTable.addRelationalClass(clazz);
			}

			getFieldDefinitions(instance, Model.getSuperclass(clazz), modelTable);
		}
	}

	/**
	 * Retrieves all fields of a given class, that are <ol> <li><b>NOT</b> private</li> <li>Database
	 * fields</li> </ol> In addition these fields are set to be accessible, so that they can then be
	 * further processed.
	 *
	 * @param clazz		  Class to extract the fields from.
	 * @param instance	Instance of that class.
	 *
	 * @return {@link List} of all fields, that are database fields, and that are <b>NOT</b>
	 *            private.
	 */
	protected final List<Field> getFields(
			Class<? extends Model> clazz,
			Model instance) {

		if(modelCache.knowsFields(clazz))
			return modelCache.fieldsForModel(clazz);

		Field[] declaredFields = clazz.getDeclaredFields();
		List<Field> fields = new ArrayList<Field>();

		try {
			for(int i = 0, length = declaredFields.length; i < length; i++) {
				Field field = declaredFields[i];

				if(!Modifier.isPrivate(field.getModifiers())) {
					field.setAccessible(true);
					Object f = field.get(instance);

					if(isDatabaseField(f))
						fields.add(field);
				}
			}
		} catch(IllegalAccessException e) {
			Log.e(TAG, "exception thrown while trying to gain access to fields of class "
					+ clazz.getSimpleName(), e);
		}

		modelCache.setModelFields(clazz, fields);

		return fields;
	}

	private final boolean isDatabaseField(Object field) {
		if(field != null)
			if(field instanceof DataField
					|| isRelationalField(field))
				return true;

		return false;
	}

	protected final boolean isRelationalField(Object field) {
		if(field != null)
			if(field instanceof ForeignKeyField
					|| field instanceof OneToManyField
					|| field instanceof ManyToManyField)
				return true;

		return false;
	}

	private final <T extends Model> List<TableDefinition> getRelationDefinitions(Class<T> clazz) {
		List<TableDefinition> definitions = new ArrayList<TableDefinition>();

		T object = getInstance(clazz);
		getRelationDefinitions(object, clazz, definitions);

		return definitions;
	}

	@SuppressWarnings("unchecked")
	private final <T extends Model> void getRelationDefinitions(
			T instance,
			Class<T> clazz,
			List<TableDefinition> definitions) {

		if(clazz != null && clazz.isInstance(instance)) {
			for(Field field : getFields(clazz, instance))
				try {
					Object o = field.get(instance);

					if(o instanceof ManyToManyField) {
						ManyToManyField<T, ?> m = (ManyToManyField<T, ?>) o;

						String leftHand = getTableName(clazz);
						String rightHand = getTableName(m.getTarget());

						TableDefinition definition = new TableDefinition(m.getRelationTableName());

						ForeignKeyField<T> leftLink = m.getLeftLinkDescriptor();
						ForeignKeyField<?> rightLink = m.getRightHandDescriptor();

						definition.addField(leftHand, leftLink);
						definition.addField(rightHand, rightLink);

						definitions.add(definition);
					}
				} catch(IllegalAccessException e) {
					Log.e(TAG, "could not gather relation definitions for class "
							+ clazz.getSimpleName(), e);
				}

			getRelationDefinitions(instance, Model.getSuperclass(clazz), definitions);
		}
	}

	ModelCache getModelCache() {
		return modelCache;
	}
	private String FOREIGN_KEY_CONSTRAINTS = "ON";

	public final void setForeignKeyConstraints(boolean on) {
		if(on)
			FOREIGN_KEY_CONSTRAINTS = "ON";
		else
			FOREIGN_KEY_CONSTRAINTS = "OFF";
	}
	// use this to mediate multi-thread access to this helper. It would have been nice to use the lock in the database
	// but it is private.
	private final ReentrantLock mLock = new ReentrantLock(true);

	void lock() {
		mLock.lock();
	}

	void unLock() {
		mLock.unlock();
	}

	/**
	 * Get a {@link Set} of model classes, that are handled by the ORM.
	 *
	 * @return {@link Set} of model classes.
	 */
	protected final Set<Class<? extends Model>> getModels() {
		return mModels;
	}

	/**
	 * Get a {@link Set} of all tables, that were created by this class.
	 *
	 * @return {@link Set} of tablenames.
	 */
	private final Set<String> getTables() {
		return mTables;
	}

	/**
	 * Drops all tables of the database.
	 *
	 * @param db {@link SQLiteDatabase}.
	 */
	protected void drop(SQLiteDatabase db) {
		db.execSQL("PRAGMA foreign_keys=OFF;");

		for(String table : getTables())
			db.execSQL("DROP TABLE IF EXISTS " + table);

		db.execSQL("PRAGMA foreign_keys=" + FOREIGN_KEY_CONSTRAINTS + ";");

		mTables.clear();
		mModels.clear();
		reset();
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		for(Class<? extends Model> model : getModels()) {
			List<TableDefinition> tableDefinitions = getTableDefinitions(model);

			for(TableDefinition definition : tableDefinitions) {
				db.execSQL(definition.toString());			// create the table
				getTables().add(definition.getTableName());	// add table to the list
				// build a list of the columns in the table now
				Cursor c = db.rawQuery(String.format("PRAGMA table_info(%s)", definition.getTableName()), null);
				Set<String> columns = new HashSet<String>(c.getCount());
				int idx = c.getColumnIndex("name");
				while(c.moveToNext())
					columns.add(c.getString(idx));
				c.close();
				// check that all the columns are in the database
				for(Entry<String, DataField<?>> entry : definition.getFields()) {
					if(!columns.contains(entry.getKey())) {
						String coldef = entry.getValue().getDefinition(entry.getKey());
						db.execSQL(String.format("alter table %s add column %s", definition.getTableName(), coldef));
					}
					// create indices for foreign key fields
					if(entry.getValue() instanceof ForeignKeyField)
						db.execSQL(String.format("create index if not exists %s_fk_idx on %s(%s)", entry.getKey(), definition.getTableName(), entry.getKey()));
				}
			}
		}
	}

	@Override
	public void onOpen(SQLiteDatabase db) {
		super.onOpen(db);

		if(!db.isReadOnly())
			// Enable foreign key constraints
			db.execSQL("PRAGMA foreign_keys=" + FOREIGN_KEY_CONSTRAINTS + ";");
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
				+ newVersion);
		onCreate(db);
	}


	public <T extends Model> QuerySet<T> objects(
			Class<T> clazz) {
		return new QuerySet<T>(clazz, this);
	}

	protected <T extends Model> T getInstance(Class<T> clazz) {
		T instance = null;
		try {
			Constructor<T> constructor = clazz.getConstructor();
			instance = constructor.newInstance();
			instance.setAdapter(this);
		} catch(Exception e) {
			Log.e(TAG, "exception thrown while trying to create representation of "
					+ clazz.getSimpleName(), e);
		}
		return instance;
	}
	protected <O extends Model, T extends Model> String getBackLinkFieldName(
			Class<O> originClass,
			Class<T> targetClass) {

		Field fk = null;

		try {
			fk = getForeignKeyField(targetClass, originClass, getInstance(originClass));
		} catch(IllegalAccessException e) {
			Log.e(TAG, "an exception has been thrown trying to gather the foreign key field pointing to "
					+ targetClass.getSimpleName()
					+ " from origin class "
					+ originClass.getSimpleName(), e);
		}

		if(fk != null)
			return fk.getName();

		return null;
	}

	protected  <T extends Model, O extends Model> Field getForeignKeyField(
			Class<T> target,
			Class<O> originClass,
			O origin) throws IllegalArgumentException, IllegalAccessException {

		Field fk = null;

		if(originClass != null && originClass.isInstance(origin)) {
			for(Field field : origin.getAdapter().getFields(originClass, origin)) {
				Object f = field.get(origin);

				if(f instanceof ForeignKeyField) {
					ForeignKeyField<?> tmp = (ForeignKeyField<?>) f;
					Class<? extends Model> t = tmp.getTarget();

					if(t.equals(target)) {
						fk = field;
						break;
					}
				}
			}

			if(fk == null)
				fk = getForeignKeyField(target, Model.getSuperclass(originClass), origin);
		}

		return fk;
	}

}
