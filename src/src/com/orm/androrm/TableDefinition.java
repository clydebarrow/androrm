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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

public class TableDefinition {

	private String mTableName;
	private Map<String, DataField<?>> mFields;
	private Map<String, ForeignKeyField<? extends Model>> mRelations;
	private List<Class<? extends Model>> mRelationalClasses;

	public TableDefinition(String tableName) {
		mFields = new HashMap<String, DataField<?>>();
		mRelations = new HashMap<String, ForeignKeyField<? extends Model>>();
		mTableName = tableName;
		mRelationalClasses = new ArrayList<Class<? extends Model>>();
	}

	public void addField(String fieldName, DataField<?> field) {
		mFields.put(fieldName, field);

		if(field instanceof ForeignKeyField)
			mRelations.put(fieldName, (ForeignKeyField<?>) field);
	}

	public <T extends Model> void addRelationalClass(Class<T> clazz) {
		mRelationalClasses.add(clazz);
	}

	public Set<Entry<String, DataField<?>>> getFields() {
		return mFields.entrySet();
	}

	private List<String> getFieldDefinitions() {
		List<String> fields = new ArrayList<String>();
		for(Entry<String, DataField<?>> entry : mFields.entrySet()) {
			DataField<?> value = entry.getValue();
			String part = value.getDefinition(entry.getKey());
			if(part.length() != 0)
				fields.add(part);
		}
		return fields;
	}

	private List<String> getConstraints() {
		List<String> fields = new ArrayList<String>();
		for(Entry<String, DataField<?>> entry : mFields.entrySet()) {
			DataField<?> value = entry.getValue();
			if(value instanceof ForeignKeyField) {
				ForeignKeyField<?> fk = (ForeignKeyField<?>) value;
				String part = fk.getConstraint(entry.getKey());
				if(part.length() != 0)
					fields.add(part);
			}
		}
		return fields;
	}

	public List<Class<? extends Model>> getRelationalClasses() {
		return mRelationalClasses;
	}

	public String getTableName() {
		return mTableName;
	}

	@Override
	public String toString() {

		List<String> fields = getFieldDefinitions();

		if(!mRelations.isEmpty())
			fields.addAll(getConstraints());

		return "CREATE TABLE IF NOT EXISTS `" + mTableName + "` (" + StringUtils.join(fields, ',') + ");";
	}
}
