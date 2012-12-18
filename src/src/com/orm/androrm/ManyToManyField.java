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
import java.util.Collections;
import java.util.List;

/**
 * @author Philipp Giese
 *
 * @param <L>	Type of the origin class.
 * @param <R>	Type of the target class.
 */
public class ManyToManyField<L extends Model, R extends Model>
		extends AbstractToManyRelation<L, R> {

	private String mTableName;

	public ManyToManyField(Class<L> origin, Class<R> target, Model model) {

		super(origin, target, model);

		mTableName = createTableName();
	}

	private String createTableName() {
		List<String> tableNames = new ArrayList<String>();
		tableNames.add(model.getAdapter().getTableName(mOriginClass));
		tableNames.add(model.getAdapter().getTableName(mTargetClass));

		Collections.sort(tableNames);

		return tableNames.get(0) + "_" + tableNames.get(1);
	}

	@Override
	public QuerySet<R> get(L origin) {
		QuerySet<R> querySet = new QuerySet<R>(mTargetClass, model.getAdapter());
		querySet.injectQuery(getQuery(origin.getId()));

		return querySet;
	}

	private JoinStatement getJoin(String leftAlias, String rightAlias, int id) {
		JoinStatement join = new JoinStatement();

		join.left(model.getAdapter().getTableName(mTargetClass), leftAlias)
				.right(getRightJoinSide(id), rightAlias)
				.on(Model.PK, model.getAdapter().getTableName(mTargetClass));

		return join;
	}

	public ForeignKeyField<L> getLeftLinkDescriptor() {
		return new ForeignKeyField<L>(mOriginClass, model);
	}

	public ForeignKeyField<R> getRightHandDescriptor() {
		return new ForeignKeyField<R>(mTargetClass, model);
	}

	private SelectStatement getQuery(int id) {
		SelectStatement select = new SelectStatement();

		select.select("a.*")
				.from(getJoin("a", "b", id));

		return select;
	}

	public String getRelationTableName() {
		return mTableName;
	}

	private SelectStatement getRightJoinSide(int id) {
		String leftTable = model.getAdapter().getTableName(mOriginClass);
		String rightTable = model.getAdapter().getTableName(mTargetClass);

		Where where = new Where();
		where.setStatement(new Statement(leftTable, id));

		SelectStatement relation = new SelectStatement();
		relation.from(mTableName)
				.select(leftTable, rightTable)
				.where(where);

		JoinStatement join = new JoinStatement();
		join.left(relation, "left")
				.right(rightTable, "right")
				.on(rightTable, Model.PK);

		SelectStatement select = new SelectStatement();
		select.from(join)
				.select("left." + rightTable + " AS " + rightTable);

		return select;
	}
}
