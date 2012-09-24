package com.orm.androrm.test.field;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import android.test.AndroidTestCase;

import com.orm.androrm.DatabaseAdapter;
import com.orm.androrm.DateField;
import com.orm.androrm.Model;
import com.orm.androrm.impl.BlankModel;

public class DateFieldTest extends AndroidTestCase {

	@Override
	public void setUp() {
		List<Class<? extends Model>> models = new ArrayList<Class<? extends Model>>();
		models.add(BlankModel.class);

		DatabaseAdapter.setDatabaseName("test_db");

		DatabaseAdapter adapter = new DatabaseAdapter();
		adapter.setModels(models);
	}

	@Override
	public void tearDown() {
		DatabaseAdapter adapter = new DatabaseAdapter();
		adapter.drop();
	}

	public void testDefaults() {
		DateField d = new DateField();

		assertEquals("foo varchar(19)", d.getDefinition("foo"));
		assertNull(d.get());
	}

	public void testDateFromString() {
		DateField d = new DateField();
		String date = "2010-11-02T12:23:43";
		Calendar	c = Calendar.getInstance();

		d.fromString(date);

		Date time = d.get();
		c.setTime(time);

		assertEquals(2010, c.get(Calendar.YEAR));
		// somehow months start at 0
		assertEquals(Calendar.NOVEMBER, c.get(Calendar.MONTH));
		assertEquals(2, c.get(Calendar.DAY_OF_MONTH));
		assertEquals(12, c.get(Calendar.HOUR_OF_DAY));
		assertEquals(23, c.get(Calendar.MINUTE));
		assertEquals(43, c.get(Calendar.SECOND));

		date = "sadjsdnksjdnf";

		d = new DateField();
		d.fromString(date);

		assertNull(d.get());
	}

	public void testGetDateString() {
		DateField d = new DateField();
		String date = "2010-11-02T12:23:43";
		d.fromString(date);

		assertEquals(date, d.getDateString());
	}

	public void testSetAndGet() {
		DateField d = new DateField();
		Calendar c = Calendar.getInstance();
		Date date = c.getTime();

		d.set(date);

		assertEquals(date, d.get());
	}

	public void testStorage() {
		Date date = Calendar.getInstance().getTime();
		BlankModel model = new BlankModel();
		model.setDate(date);
		model.save();

		assertEquals(date, model.getDate());

		model = Model.objects(BlankModel.class).get(model.getId());
		Date newDate = model.getDate();

		assertNotNull(newDate);

		assertEquals(date.getTime(), newDate.getTime());
	}
}
