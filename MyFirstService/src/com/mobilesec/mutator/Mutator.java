package com.mobilesec.mutator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Random;

import android.os.Bundle;

public class Mutator {
	// Class name strings
	private static final String CLASS_BOOLEAN = "java.lang.Boolean";
	private static final String CLASS_BYTE = "java.lang.Byte";
	private static final String CLASS_CHARACTER = "java.lang.Character";
	private static final String CLASS_DOUBLE = "java.lang.Double";
	private static final String CLASS_FLOAT = "java.lang.Float";
	private static final String CLASS_INTEGER = "java.lang.Integer";
	private static final String CLASS_LONG = "java.lang.Long";
	private static final String CLASS_STRING = "java.lang.String";
	
	// Fuzzer paramters
	private long seed;
	private float ratio;
	private Random r;
	
	
	/*
	 * Mutator constructor
	 */
	public Mutator(long seed, float ratio) {
		this.seed = seed;
		this.ratio = ratio;
		
		r = new Random();
		r.setSeed(seed);
		
		return;
	}
	
	
/* ---------------------------------------------------------------------
 * Conservative Mutation
 * 
 * These guys will mutate anything we can mutate without resorting to
 * mutating the entire object.
 * --------------------------------------------------------------------- */

	/*
	 * mutate -- mutates a boolean
	 */
	public boolean mutate(boolean b) {
		if (r.nextFloat() < ratio)
			b = !b;
		return b;
	}
	
	/*
	 * mutate -- mutates a byte
	 */
	public byte mutate(byte b) {
		return mutateByte(b);
	}
	
	/*
	 * mutate -- mutates a bundle
	 */
	public Bundle mutate(Bundle b)
	{
		Bundle mut = mutateBundle(b);
		b.putAll(mut);
		return b;
	}
	
	/*
	 * mutate -- mutates a character
	 */
	public char mutate(char c) {
		return mutateChar(c);
	}
	
	/*
	 * mutate -- mutates a double
	 */
	public double mutate(double d) {
		long fuzzed = mutateLong(Double.doubleToRawLongBits(d));
		return Double.longBitsToDouble(fuzzed);
	}
	
	/*
	 * mutate -- mutates a float
	 */
	public float mutate(float f) {
		int fuzzed = mutateInt(Float.floatToRawIntBits(f));
		return Float.intBitsToFloat(fuzzed);
	}
	
	/*
	 * mutate -- mutates an integer
	 */
	public int mutate(int i) {
		return mutateInt(i);
	}
	
	/*
	 * mutate -- mutates a long
	 */
	public long mutate(long l) {
		return mutateLong(l);
	}
	
	/*
	 * mutate -- mutates a string
	 */
	public String mutate(String str)
	{
		String fuzzed = new String();
		
		for (int i = 0; i < str.length(); ++i)
			fuzzed += mutateChar(str.charAt(i));
		
		return fuzzed;
	}
	
	
/* ---------------------------------------------------------------------
 * Aggressive Mutation
 * 
 * These functions mutate a little harder (I think), and they can throw
 * exceptions
 * --------------------------------------------------------------------- */
	
	
	/*
	 * mangle -- mangle an Android Bundle
	 */
	public Bundle mangle(Bundle b)
	throws IOException, ClassNotFoundException
	{
		Bundle mut = mutateBundle(b);

        for (String key : b.keySet()) {
            Object obj = b.get(key);
            String name = obj.getClass().getName();
        	if (name.equals(CLASS_STRING))
        		b.putString(key, mangle((String) obj));
        }
		
		b.putAll(mut);
		return b;
	}
	
	/*
	 * mangle -- mangle a string
	 * 
	 * NOTE: This guy is here partly because I'm not terribly comfortable
	 * with the encoding stuff.
	 */
	public String mangle(String str)
	throws UnsupportedEncodingException
	{
		byte[] bytes = str.getBytes();
		
		for (int i = 0; i < bytes.length; ++i)
			bytes[i] = mutateByte(bytes[i]);
		
		return new String(bytes, 0, bytes.length, "ASCII");
	}
	
	
/* ---------------------------------------------------------------------
 * Reckless Mutation
 * 
 * This function will mutate anything, and it's meta-data.
 * --------------------------------------------------------------------- */
	
	/*
	 * brutalize -- mutate a Java object
	 * 
	 * This guy has no notion of the object's structure--he'll mutate
	 * EVERYTHING.  Furthermore, when you get your object back. it's not
	 * even an object anymore--it has been reduced to raw bytes.  In short,
	 * this guy is a cold and heartless monster.
	 */
	public byte[] brutalize(Object obj)
	throws IOException
	{
	    ByteArrayOutputStream out = new ByteArrayOutputStream();
	    ObjectOutputStream os = new ObjectOutputStream(out);
	    os.writeObject(obj);
		byte[] bytes = out.toByteArray();
		
		for (int i = 0; i < bytes.length; ++i)
			bytes[i] = mutateByte(bytes[i]);
		
		return bytes;
	}
	
	
/* ---------------------------------------------------------------------
 * Core Mutation
 * 
 * These are internal mutation functions; most everything eventually calls
 * these functions.
 * --------------------------------------------------------------------- */
	
	/*
	 * mutateByte -- mutates a byte
	 */
	private byte mutateByte(byte b) {
		for (int i = 0; i < Byte.SIZE; ++i)
			if (r.nextFloat() < ratio)
				b ^= 1 << i;
		return b;
	}
	
	/*
	 * mutateBundle -- conservatively mutate the items within a bundle
	 * 
	 * NOTE: this function returns a bundle containing ONLY the
	 * mutated items.
	 */
	private Bundle mutateBundle(Bundle bundle)
	{
		Bundle fuzzed = new Bundle();
		
        for (String key : bundle.keySet()) {
            Object obj = bundle.get(key);
            if(obj == null) continue;
            String name = obj.getClass().getName();

        	// Yup, this is happening
            if (name.equals(CLASS_BOOLEAN))
        		fuzzed.putBoolean(key, mutate(bundle.getBoolean(key)));
            else if (name.equals(CLASS_BYTE))
        		fuzzed.putByte(key, mutate(bundle.getByte(key)));
        	else if (name.equals(CLASS_CHARACTER))
        		fuzzed.putChar(key, mutate((Character) obj));
        	else if (name.equals(CLASS_DOUBLE))
        		fuzzed.putDouble(key, mutate((Double) obj));
            else if (name.equals(CLASS_FLOAT))
        		fuzzed.putFloat(key, mutate(bundle.getFloat(key)));
        	else if (name.equals(CLASS_INTEGER))
        		fuzzed.putInt(key, mutate((Integer) obj));
        	else if (name.equals(CLASS_LONG))
        		fuzzed.putLong(key, mutate((Long) obj));
        	else if (name.equals(CLASS_STRING))
        		fuzzed.putString(key, mutate((String) obj));
        }
        
        return fuzzed;
	}
	
	/*
	 * mutateChar -- mutates an character
	 */
	private char mutateChar(char c) {
		for (int i = 0; i < Character.SIZE; ++i)
			if (r.nextFloat() < ratio)
				c ^= 1 << i;
		return c;
	}
	
	/*
	 * mutateInt -- mutates an integer
	 */
	private int mutateInt(int n) {
		for (int i = 0; i < Integer.SIZE; ++i)
			if (r.nextFloat() < ratio)
				n ^= 1 << i;
		return n;
	}
	
	/*
	 * mutateLong -- mutates a long
	 */
	private long mutateLong(long l) {
		for (int i = 0; i < Long.SIZE; ++i)
			if (r.nextFloat() < ratio)
				l ^= 1 << i;
		return l;
	}
	
	
/* ---------------------------------------------------------------------
 * Getters and Setters
 * 
 * Careful setting the seed.  Generally you want the seed to be constant
 * throughout your mutations.
 * --------------------------------------------------------------------- */

	/*
	 * {g,s}etSeed
	 */
	public long getSeed() {return this.seed;}
	public void setSeed(long s) {this.seed = s;}

	/*
	 * {g,s}etRatio
	 */
	public float getRatio() {return this.ratio;}
	public void setRatio(float r) {this.ratio = r;}
	
}
