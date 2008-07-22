/*

   Derby - Class org.apache.derby.iapi.services.sanity.AssertFailure

   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to you under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

 */

package org.apache.derby.shared.common.sanity;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.AccessControlException;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

/**
 * AssertFailure is raised when an ASSERT check fails. Because assertions are
 * not used in production code, are never expected to fail, and recovering from
 * their failure is expected to be hard, they are under RuntimeException so that
 * no one needs to list them in their throws clauses. An AssertFailure at the
 * outermost system level will result in system shutdown.
 * 
 * An AssertFailure also contains a string representation of a full thread dump 
 * for all the live threads at the moment it was thrown if the JVM supports it 
 * and we have the right permissions. 
 * 
 * If the JVM doesn't have the method Thread.getAllStackTraces i.e, we are on a
 * JVM < 1.5, or if we don't have the permissions java.lang.RuntimePermission
 * "getStackTrace" and "modifyThreadGroup", a message saying so is stored
 * instead.
 * 
 * The thread dump string is printed to System.err after the normal stack trace 
 * when the error is thrown, and it is also directly available by getThreadDump().
 */
public class AssertFailure extends RuntimeException {
    
    private String threadDump;
    
	/**
	 * This constructor takes the pieces of information
	 * expected for each error.
	 *
	 * @param message the message associated with
	 * the error.
	 *
	 * @param nestedError errors can be nested together;
	 * if this error has another error associated with it,
	 * it is specified here. The 'outermost' error should be
	 * the most sever error; inner errors should be providing
	 * additional information about what went wrong.
	 **/
	public AssertFailure(String message, Throwable nestedError) {   
		super(message, nestedError);
		threadDump = dumpThreads();
	}
	
	/**
     * This constructor takes the just the message for this error.
     *
     * @param message the message associated with the error.
     **/
	public AssertFailure(String message) {
        super(message);
        threadDump = dumpThreads();
    }
	
	/**
	 * This constructor expects no arguments or nested error.
	 **/
	public AssertFailure() {
		super();
		threadDump = dumpThreads();
	}
	
	/**
	 * Returns the thread dump stored in this AssertFailure as a string.
	 * 
	 * @return - thread dump string.
	 */
	public String getThreadDump() {
	    return threadDump;
	}
	
	/**
	 * Overrides printStackTrace() in java.lang.Throwable to include
	 * the thread dump after the normal stack trace. 
	 */
	
	public void printStackTrace() {
	    printStackTrace(System.err);
	}
	
	/**
     * Overrides printStackTrace(PrintStream s) in java.lang.Throwable 
     * to include the thread dump after the normal stack trace. 
     */
	public void printStackTrace(PrintStream s) {
	    super.printStackTrace(s);
	    s.println(threadDump);
    }
	
	/**
     * Overrides printStackTrace(PrintWriter s) in java.lang.Throwable 
     * to include the thread dump after the normal stack trace. 
     */
	public void printStackTrace(PrintWriter s) { 
	    super.printStackTrace(s);
        s.println(threadDump);
	}
	
	/**
	 * Dumps stack traces for all the threads if the JVM supports it.
	 * The result is returned as a string, ready to print.
	 * 
	 * If the JVM doesn't have the method Thread.getAllStackTraces 
	 * i.e, we are on a JVM < 1.5, or  if we don't have the permissions: 
	 * java.lang.RuntimePermission "getStackTrace" and "modifyThreadGroup",
	 * a message saying so is returned instead. 
	 * 
	 * @return stack traces for all live threads as a string or an error message.
	 */
	private String dumpThreads() {
	    PrintWriter p;
	    ByteArrayOutputStream out = new ByteArrayOutputStream();

	    p = new PrintWriter(out, true);
	    
	    //Try to get a thread dump and deal with various situations.
	    try {
	        try {
	            //This checks that we are on a jvm >= 1.5 where we 
	            //can actually do threaddumps.
	            Class c = Class.forName("java.lang.Thread");
	            c.getMethod("getAllStackTraces", new Class[] {});
	            
	            //Then get the thread dump.
	            c = Class.forName("org.apache.derby.shared.common.sanity.ThreadDump");
                final Method m = c.getMethod("getStackDumpString",new Class[] {});
                
                String dump;
                try {
                    dump = (String) AccessController.doPrivileged
                        (new PrivilegedExceptionAction(){
                                public Object run() throws 
                                    IllegalArgumentException, 
                                    IllegalAccessException, 
                                    InvocationTargetException{
                                    return m.invoke(null, null);
                                }
                            }
                         );
                } catch (PrivilegedActionException e) {
                    throw  e.getException();
                }
                //Print the dump to the message string. That went OK.
                p.print("---------------\nStack traces for all " +
                "live threads:");
                p.println("\n" + dump);
                p.println("---------------");
	        
	        } catch (NoSuchMethodException e) {
	            p.println("(Skipping thread dump because it is not " +
	            		"supported on JVM 1.4)");
	        }
	    } catch (Exception e){ 
            if (e instanceof InvocationTargetException) {
            	if (((InvocationTargetException) e).getTargetException() 
            			instanceof AccessControlException) {
            		p.println("(Skipping thread dump " +
            				"because of insufficient permissions:\n" 
            		        + e.getCause() + ")\n" );
            	} else 
            		((InvocationTargetException) e).getTargetException().
            		printStackTrace(p);
            } else {
            	p.println("Error trying to dump thread stack traces:");
                e.printStackTrace(p);
            }
        }
	    return out.toString();
	}
}
