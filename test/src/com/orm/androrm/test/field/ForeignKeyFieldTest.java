package com.orm.androrm.test.field;

import java.util.ArrayList;
import java.util.List;

import android.os.Build;
import android.test.AndroidTestCase;

import com.orm.androrm.DatabaseAdapter;
import com.orm.androrm.DatabaseBuilder;
import com.orm.androrm.ForeignKeyField;
import com.orm.androrm.Model;
import com.orm.androrm.impl.Branch;
import com.orm.androrm.impl.Brand;
import com.orm.androrm.impl.Product;
import com.orm.androrm.impl.Supplier;

public class ForeignKeyFieldTest extends AndroidTestCase {

	@Override
	public void setUp() {
		List<Class<? extends Model>> models = new ArrayList<Class<? extends Model>>();
		models.add(Product.class);
		models.add(Branch.class);
		models.add(Brand.class);
		models.add(Supplier.class);
		
		DatabaseAdapter.setDatabaseName("test_db");
		
		DatabaseAdapter adapter = new DatabaseAdapter();
		adapter.setModels(models);
	}
	
	public void testDoCascade() {
		if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.ECLAIR_MR1) {
			return;
		}
		
		Brand b = new Brand();
		b.setName("Copcal");
		b.save();
		
		Branch br = new Branch();
		br.setBrand(b);
		br.setName("Pretoria");
		br.save();
		
		b.delete();
		
		assertEquals(0, Branch.objects(getContext()).count());
	}
	
	public void testDoNotCascade() {
		Brand b = new Brand();
		b.setName("Copcal");
		b.save();
		
		Supplier s = new Supplier();
		s.setName("test_supplier");
		s.setBrand(b);
		s.save();
		
		b.delete();
		
		assertEquals(1, Supplier.objects(getContext()).count());
	}
	
	public void testGetDefauls() {
		ForeignKeyField<Product> fk = new ForeignKeyField<Product>(Product.class);
		String targetTable = DatabaseBuilder.getTableName(Product.class);
		
		assertEquals("product_id integer", fk.getDefinition("product_id"));
		assertEquals("FOREIGN KEY (product_id) REFERENCES " 
					+ targetTable 
					+ " (" 
					+ Model.PK 
					+ ") ON DELETE CASCADE", fk.getConstraint("product_id"));
		
		assertNull(fk.get());
	}
	
	public void testGetDefaultsNoCascade() {
		ForeignKeyField<Product> fk = new ForeignKeyField<Product>(Product.class);
		fk.doNotCascade();
		
		String targetTable = DatabaseBuilder.getTableName(Product.class);
		
		assertEquals("product_id integer", fk.getDefinition("product_id"));
		assertEquals("FOREIGN KEY (product_id) REFERENCES " 
					+ targetTable 
					+ " (" 
					+ Model.PK 
					+ ") ON DELETE SET NULL", fk.getConstraint("product_id"));
		
		assertNull(fk.get());
	}
	
	public void testIsPersisted() {
		ForeignKeyField<Product> fk = new ForeignKeyField<Product>(Product.class);
		Product p = new Product();
		
		assertFalse(fk.isPersisted());
		
		fk.set(p);
		
		assertFalse(fk.isPersisted());
		p.save();
		
		assertTrue(fk.isPersisted());
	}
	
	public void testGetTarget() {
		ForeignKeyField<Product> fk = new ForeignKeyField<Product>(Product.class);
		
		assertEquals(Product.class, fk.getTarget());
	}
	
	public void testSetAndGet() {
		Product p = new Product();
		p.setName("test product");
		
		ForeignKeyField<Product> fk = new ForeignKeyField<Product>(Product.class);
		
		fk.set(p);
		assertEquals(p.getName(), fk.get().getName());
		
		p.save();
		
		fk = new ForeignKeyField<Product>(Product.class);
		fk.set(p.getId());
		assertEquals(p.getName(), fk.get().getName());
	}
	
	public void testReset() {
		Product p = new Product();
		p.setName("test product");
		p.save();
		
		ForeignKeyField<Product> fk = new ForeignKeyField<Product>(Product.class);
		
		fk.set(p);
		fk.reset();
		
		assertNull(fk.get());
		
		fk.set(p.getId());
		fk.reset();
		
		assertNull(fk.get());
	}
	
	@Override
	public void tearDown() {
		DatabaseAdapter adapter = new DatabaseAdapter();
		adapter.drop();
	}
}
