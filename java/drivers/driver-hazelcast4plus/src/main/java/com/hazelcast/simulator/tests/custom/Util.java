package com.hazelcast.simulator.tests.custom;

import java.lang.reflect.Method;

public class Util {
	

	

	// This will create a DAO class using String[] = <method name> <value>
	public static Object createCCTrans(String[] row, Class<?> clazz, String[] header) {

		Object ccTrans = null;
		try {
			ccTrans = clazz.getDeclaredConstructor().newInstance();

			for (int i = 0; i < header.length; i++) {
				
				Method method;
				String methodName = Util.createMethodName(header[i].toLowerCase());
				try{
					method = clazz.getDeclaredMethod(methodName, Long.class);
					method.invoke(ccTrans, Long.valueOf(row[i]));
				} catch (NumberFormatException | NoSuchMethodException e) {
					try{
						method = clazz.getDeclaredMethod(methodName, Double.class);
						method.invoke(ccTrans, Double.valueOf(row[i]));
					} catch (NumberFormatException | NoSuchMethodException ine) {
						//looks like a String
						method = clazz.getDeclaredMethod(methodName, String.class);
						method.invoke(ccTrans, row[i]);
					}
				}
				
				// switch(values.get(i)[1]) {
				// case "Double":
				// 	method = clazz.getDeclaredMethod(methodName, Double.class);
				// 	method.invoke(ccTrans, Double.valueOf(values.get(i)[2]));
				// 	break;
				// case "String":
				// 	method = clazz.getDeclaredMethod(methodName, String.class);
				// 	method.invoke(ccTrans, values.get(i)[2]);
				// 	break;
				// case "Int":
				// 	method = clazz.getDeclaredMethod(methodName, Integer.class);
				// 	method.invoke(ccTrans, Integer.valueOf(values.get(i)[2]));
				// 	break;
				// default:
				// 	method = clazz.getDeclaredMethod(methodName, Double.class);
				// 	method.invoke(ccTrans, Double.valueOf(values.get(i)[2]));
				// }
			}
		} catch (Exception e) {
			throw new RuntimeException("Can't instantiate class", e);
		}
		return ccTrans;
	}
	
	// This fixes some method names to conform to Eclipse naming conventions for getters and setters
	public static String createMethodName(String fieldName) {
		
		if (Character.isLowerCase(fieldName.charAt(0))) {
			StringBuffer str = new StringBuffer(fieldName);
			str.replace(0, 1, Character.toUpperCase(fieldName.charAt(0)) + "");
			return "set" + str;
		} else {
			return "set" + fieldName;
		}
		
	}
	

}
