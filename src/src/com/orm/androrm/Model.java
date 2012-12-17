/**
 * Copyright (c) 2010 Philipp Giese
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

import android.content.ContentValues;
import android.database.Cursor;
import android.util.Log;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * This is the superclass of all models, that can be stored/ read to/ from the database
 * automatically.
 *
 * @author Philipp Giese
 */
public abstract class Model implements Comparable<Model> {

	public DatabaseAdapter getAdapter() {
		return mAdapter;
	}

	protected class PrimaryKeyField extends IntegerField {

		private boolean mAutoIncrement;

		public PrimaryKeyField() {
			this(true);
		}

		public PrimaryKeyField(boolean autoincrement) {
			mAutoIncrement = autoincrement;
		}

		@Override
		public String getDefinition(String fieldName) {
			String definition = super.getDefinition(fieldName)
					+ " PRIMARY KEY";

			if(mAutoIncrement)
				definition += " autoincrement";

			return definition;
		}

		public boolean isAutoincrement() {
			return mAutoIncrement;
		}
	}
	private static final String TAG = "ANDRORM:MODEL";
	/**
	 * Name used for the primary key field, that is automatically assigned to each model.
	 */
	public static final String PK = "mId";
	public static final String COUNT = "item_count";
	private DatabaseAdapter mAdapter;

	/**
	 * Assigns a value gathered from the database to the instance
	 * <code>object</b> of type T. Due to the nature
	 * of this ORM only fields applicable for serialization
	 * will be considered.
	 *
	 * @param <T>		  Type of the object.
	 * @param field	 Field of the object, that a value shall be assigned to.
	 * @param object	Object instance of type <T>.
	 * @param c	     Database {@link Cursor}
	 *
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 */
	private <T extends Model> void assignFieldValue(
			Field field,
			Cursor c) throws IllegalArgumentException, IllegalAccessException {

		Object o = field.get(this);

		if(o instanceof DataField) {
			DataField<?> f = (DataField<?>) o;

			f.set(c, field.getName());
		}
	}

	protected static <T extends Model> T createObject(
			Class<T> clazz,
			Cursor c, DatabaseAdapter adapter) {

		T object = getInstance(clazz, adapter);

		try {
			object.fillUpData(clazz, c);
		} catch(IllegalAccessException e) {
			Log.e(TAG, "exception thrown while filling instance of "
					+ clazz.getSimpleName()
					+ " with data.", e);
		}

		return object;
	}

	protected <T extends Model> void fillUpData(
			Class<T> clazz,
			Cursor c) throws IllegalArgumentException, IllegalAccessException {

		if(clazz != null && clazz.isInstance(this)) {

			for(Field field : mAdapter.getFields(clazz, this))
				assignFieldValue(field, c);

			fillUpData(getSuperclass(clazz), c);
		}
	}

	protected static <O extends Model, T extends Model> String getBackLinkFieldName(
			Class<O> originClass,
			Class<T> targetClass,
			DatabaseAdapter adapter) {

		Field fk = null;

		try {
			fk = getForeignKeyField(targetClass, originClass, getInstance(originClass, adapter));
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

	private <T extends Model> List<String> getEligibleFields(
			Class<? extends Model> clazz,
			T instance) {

		List<String> eligableFields = new ArrayList<String>();

		if(clazz != null) {
			for(Field field : mAdapter.getFields(clazz, instance))
				eligableFields.add(field.getName());

			eligableFields.addAll(getEligibleFields(getSuperclass(clazz), instance));
		}

		return eligableFields;
	}

	private <T extends Model> void raiseFieldExecption(T instance, String fieldName) {
		throw new NoSuchFieldException("No field named "
				+ fieldName
				+ " was found in class "
				+ instance.getClass().getSimpleName()
				+ "! Choices are: "
				+ getEligibleFields(instance.getClass(), instance).toString());
	}

	protected <T extends Model> Field getField(
			Class<T> clazz,
			T instance,
			String fieldName) {

		Field field = null;

		if(clazz != null) {
			if(getModelCache().knowsFields(clazz))
				if(getModelCache().modelHasField(clazz, fieldName))
					field = getModelCache().getField(clazz, fieldName);

			if(field == null)
				field = getField(getSuperclass(clazz), instance, fieldName);

			if(field == null)
				raiseFieldExecption(instance, fieldName);

		}

		return field;
	}

	@SuppressWarnings("unchecked")
	private <T extends Model, O extends Model> ForeignKeyField<T> getForeignKey(
			O origin,
			Class<O> originClass,
			Class<T> target) throws IllegalArgumentException, IllegalAccessException {

		if(originClass != null && originClass.isInstance(origin)) {
			Field fkField = getForeignKeyField(target, originClass, origin);

			if(fkField != null)
				return (ForeignKeyField<T>) fkField.get(origin);
		}

		return null;
	}

	private static <T extends Model, O extends Model> Field getForeignKeyField(
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
				fk = getForeignKeyField(target, getSuperclass(originClass), origin);
		}

		return fk;
	}

	protected static <T extends Model> T getInstance(Class<T> clazz, DatabaseAdapter adapter) {
		T instance = null;

		try {
			Constructor<T> constructor = clazz.getConstructor();
			instance = constructor.newInstance();
			instance.setAdapter(adapter);
		} catch(Exception e) {
			Log.e(TAG, "exception thrown while trying to create representation of "
					+ clazz.getSimpleName(), e);
		}

		return instance;
	}

	@SuppressWarnings("unchecked")
	protected static <T extends Model, U extends Model> Class<U> getSuperclass(Class<T> clazz) {
		Class<?> parent = clazz.getSuperclass();
		Class<U> superclass = null;

		if(!parent.equals(Object.class))
			superclass = (Class<U>) parent;

		return superclass;
	}

	private <T extends Model, O extends Model> void setBackLink(
			T target,
			Class<T> targetClass,
			O origin,
			Class<O> originClass) throws NoSuchFieldException {

		ForeignKeyField<T> fk = null;

		try {
			fk = getForeignKey(origin, originClass, targetClass);
		} catch(IllegalAccessException e) {
			Log.e(TAG, "an exception was thrown trying to gather a foreign key field pointing to "
					+ targetClass.getSimpleName()
					+ " on an instance of class "
					+ originClass.getSimpleName(), e);
		}

		if(fk != null)
			fk.set(target);
		else
			throw new NoSuchFieldException("No field pointing to "
					+ targetClass.getSimpleName()
					+ " was found in class "
					+ originClass.getSimpleName()
					+ "! Choices are: "
					+ getEligibleFields(originClass, origin).toString());
	}
	protected PrimaryKeyField mId;

	public void setAdapter(DatabaseAdapter adapter) {
		mAdapter = adapter;
	}

	public Model(boolean suppressAutoincrement) {
		mId = new PrimaryKeyField(!suppressAutoincrement);
	}

	private <T extends Model> void collectData(
			ContentValues values,
			Class<T> clazz) throws IllegalArgumentException, IllegalAccessException {

		if(clazz != null && clazz.isInstance(this)) {
			for(Field field : mAdapter.getFields(clazz, this)) {
				Object o = field.get(this);
				String fieldName = field.getName();

				putValue(o, fieldName, values);
			}

			collectData(values, getSuperclass(clazz));
		}
	}

	public <T extends Model> boolean delete() {
		if(getId() != 0) {
			Where where = new Where();
			where.and(PK, getId());

			int affectedRows = mAdapter.delete(mAdapter.getTableName(getClass()), where);

			if(affectedRows != 0) {
				setId(0);

				return resetFields();
			}
		}

		return false;
	}

	private <T extends Model> boolean resetFields() {
		List<Field> fields = mAdapter.getFields(getClass(), this);

		try {
			for(Field field : fields) {
				Object o = field.get(this);

				if(o instanceof AndrormField) {
					AndrormField f = (AndrormField) o;
					f.reset();
				}
			}

			return true;
		} catch(IllegalAccessException e) {
			return false;
		}
	}

	@Override
	public boolean equals(Object o) {
		if(o instanceof Model) {
			Model m = (Model) o;

			if(getClass().equals(m.getClass())
					&& getId() == m.getId())
				return true;
		}

		return false;
	}

	public int getId() {
		return mId.get();
	}

	public void setId(int id) {
		mId.set(id);
	}

	private boolean handledByPrimaryKey(Object field) {
		if(field instanceof PrimaryKeyField) {
			PrimaryKeyField pk = (PrimaryKeyField) field;
			return pk.isAutoincrement();
		}

		return false;
	}

	@Override
	public int hashCode() {
		return getId() + getClass().getSimpleName().hashCode();
	}

	private <T extends Model, O extends Model> void persistRelations(
			Class<T> clazz) throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException {

		if(clazz != null && clazz.isInstance(this)) {

			for(Field field : mAdapter.getFields(clazz, this)) {
				Object o = field.get(this);

				if(o instanceof ManyToManyField)
					saveM2MToDatabase(clazz, o);

				if(o instanceof OneToManyField)
					saveO2MToDatabase(o);
			}

			persistRelations(getSuperclass(clazz));
		}
	}

	private void putValue(
			Object field,
			String fieldName,
			ContentValues values) {

		if(field instanceof DataField
				&& !handledByPrimaryKey(field)) {

			DataField<?> f = (DataField<?>) field;
			f.putData(fieldName, values);
		}
	}

	public boolean save() {
		if(mId.isAutoincrement() || getId() != 0)
			return save(getId(), new ContentValues());

		return false;
	}

	public boolean save(int id) {
		if(!mId.isAutoincrement()) {
			setId(id);

			ContentValues values = new ContentValues();
			values.put(PK, id);

			return save(id, values);
		}

		return false;
	}

	private <T extends Model> boolean save(
			int id,
			ContentValues values) {

		try {
			collectData(values, getClass());
		} catch(IllegalAccessException e) {
			Log.e(TAG, "exception thrown while gathering data from object", e);
		}

		Where where = new Where();
		where.and(PK, id);

		int rowID = mAdapter.doInsertOrUpdate(mAdapter.getTableName(getClass()), values, where);

		if(rowID == -1) {
			setId(0);
			return false;
		}

		if(getId() == 0)
			setId(rowID);

		try {
			persistRelations(getClass());
		} catch(Exception e) {
			Log.e(TAG, "an exception has been thrown trying to save the relations for "
					+ getClass().getSimpleName(), e);

			return false;
		}

		return rowID != 0;
	}

	@SuppressWarnings("unchecked")
	private <T extends Model> void saveM2MToDatabase(
			Class<T> clazz,
			Object field) {

		ManyToManyField<T, ?> m = (ManyToManyField<T, ?>) field;
		List<? extends Model> targets = m.getCachedValues();

		for(Model target : targets)
			/*
			 * Only save relation to the database if the
			 * target model has been persisted. 
			 */
			if(target.getId() != 0) {
				ContentValues values = new ContentValues();
				Where where = new Where();
				where.and(mAdapter.getTableName(clazz), getId())
						.and(mAdapter.getTableName(m.getTarget()), target.getId());

				values.put(mAdapter.getTableName(clazz), getId());
				values.put(mAdapter.getTableName(m.getTarget()), target.getId());

				mAdapter.doInsertOrUpdate(m.getRelationTableName(), values, where);
			}
	}

	@SuppressWarnings("unchecked")
	private <O extends Model, T extends Model> void saveO2MToDatabase(
			Object field) throws NoSuchFieldException {

		OneToManyField<T, ?> om = (OneToManyField<T, ?>) field;
		List<? extends Model> targets = om.getCachedValues();

		for(Model target : targets)
			/*
			 * Only save the target, if it has already been saved once to the database.
			 * Otherwise we could save objects, that shouldn't be saved. 
			 */
			if(target.getId() != 0) {
				setBackLink((T) this, (Class<T>) getClass(), (O) target, (Class<O>) target.getClass());
				target.save();
			}
	}

	@Override
	public int compareTo(Model model) {
		return getId() - model.getId();
	}

	public <T extends Model> QuerySet<T> objects(
			Class<T> clazz) {
		return mAdapter.objects(clazz);
	}

	protected ModelCache getModelCache() {
		return mAdapter.getModelCache();
	}
}
