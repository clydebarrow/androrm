package com.orm.androrm.test;

import java.util.ArrayList;
import java.util.List;

import android.test.AndroidTestCase;

import com.orm.androrm.DatabaseAdapter;
import com.orm.androrm.Model;
import com.orm.androrm.impl.BlankModel;
import com.orm.androrm.impl.BlankModelNoAutoincrement;

public class ModelTest extends AndroidTestCase {

	@Override
	public void setUp() {
		List<Class<? extends Model>> models = new ArrayList<Class<? extends Model>>();
		models.add(BlankModel.class);
		models.add(BlankModelNoAutoincrement.class);
		
		DatabaseAdapter.setDatabaseName("test_db");
		
		DatabaseAdapter adapter = new DatabaseAdapter();
		adapter.setModels(models);
	}
	
	public void testInitialId() {
		Model m = new BlankModel();
		assertEquals(0, m.getId());
		
		m = new BlankModelNoAutoincrement();
		assertEquals(0, m.getId());
	}
	
	public void testSave() {
		Model m = new BlankModel();

		assertTrue(m.save());
		assertEquals(1, m.getId());
		
		assertFalse(m.save(5));
	}
	
	public void testSaveAutoincrementOverwrite() {
		Model m = new BlankModelNoAutoincrement();

		// initial id has to be given
		assertFalse(m.save());
		assertTrue(m.save(5));
		assertEquals(5, m.getId());
		// after id is set, regular save function works
		assertTrue(m.save());
	}
	
	public void testDelete() {
		BlankModel m = new BlankModel();
		
		assertFalse(m.delete());
		
		m.save();
		
		int id = m.getId();
		
		assertTrue(m.delete());
		assertNull(m.getName());
		assertNull(m.getLocation());
		assertNull(m.getDate());
		assertNull(Model.objects(BlankModel.class).get(id));
		assertEquals(0, m.getId());
	}
	
	public void testEquals() {
		BlankModel m = new BlankModel();
		m.setName("test");
		m.save();
		
		BlankModel m2 = Model.objects(BlankModel.class).get(m.getId());
		
		assertEquals(m, m2);
		
		BlankModelNoAutoincrement m3 = new BlankModelNoAutoincrement();
		m3.save(1);
		
		assertFalse(m.equals(m3));
	}
	
	@Override
	public void tearDown() {
		DatabaseAdapter adapter = new DatabaseAdapter();
		adapter.drop();
	}
}
