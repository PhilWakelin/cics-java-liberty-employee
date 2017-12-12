/* Licensed Materials - Property of IBM                                   */
/*                                                                        */
/* SAMPLE                                                                 */
/*                                                                        */
/* (c) Copyright IBM Corp. 2017 All Rights Reserved                       */
/*                                                                        */
/* US Government Users Restricted Rights - Use, duplication or disclosure */
/* restricted by GSA ADP Schedule Contract with IBM Corp                  */
/*                                                                        */
package com.ibm.cicsdev.employee.jdbc.impl;

import javax.naming.InitialContext;
import javax.sql.DataSource;
import javax.transaction.UserTransaction;

import com.ibm.cics.server.TSQ;
import com.ibm.cicsdev.employee.jdbc.beans.Employee;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

/**
 * This class contains all of the database interaction
 * code for our application.
 * 
 * Data is passed in from either EmpListBean or AddEmpBean
 * to one of the update methods.
 * 
 * Those methods will then attempt to perform the methods.
 * Once complete they will return control to the caller,
 * which will print out a message to screen.
 * 
 * @author Michael Jones
 *
 */
public class DbOperations {
    
    /**
     * Name of CICS TSQ used to log activity.
     */
    private static final String TSQ_NAME = "DB2LOG"; 

    /**
     * Uses a specified last name to find a matching employee
     * in the database table.
     * 
     * Used by the search function on master.xhtml page
     * 
     * @return
     */
    public static ArrayList<Employee> findEmployeeByLastName(DataSource ds, String lastName) throws SQLException
    {
        // Instances of JDBC objects
        Connection conn = null;
        PreparedStatement statement = null;
        
        try {
            // The SQL command used to find our employees list
            String sqlCmd = "SELECT " +
                                "BIRTHDATE, BONUS, COMM, EDLEVEL, EMPNO, " +
                                "FIRSTNME, HIREDATE, JOB, LASTNAME, MIDINIT, " +
                                "PHONENO, SALARY, SEX, WORKDEPT " +
                            "FROM EMP  WHERE LASTNAME LIKE ? ORDER BY LASTNAME, EMPNO";
            
            // Get the DB connection
            conn = ds.getConnection();
            
            // Prepare the statement, uppercase lastname and set as first query value
            statement = conn.prepareStatement(sqlCmd);
            statement.setString(1, lastName.toUpperCase() + "%");
            
            // Perform the SELECT operation
            ResultSet rs = statement.executeQuery();
            
            // Store any results in the Employee bean list
            ArrayList<Employee> results = new ArrayList<Employee>();
            while ( rs.next() ) {
                results.add(createEmployeeBean(rs));
            }
            
            // Return the full list
            return results;
        }
        finally {
            
            // Any exceptions will be propagated
            
            // Close database objects, regardless of what happened
            if ( statement != null ) {
                statement.close();
            }
            if ( conn != null ) {
                conn.close();
            }
        }
    }
    
    /**
     * Writes a new employee to the database.
     * 
     * This method is called when a user presses 'Add employee' button.
     * It will add the employee based on the values provided in the already-populated bean
     * 
     * @param ds - The target data source
     * @param employee - The employee object populated
     * @param useJta - use JTA to provide unit of work support, rather than the
     * CICS unit of work support
     * 
     * @throws Exception All exceptions are propagated from this method.
     */
    public static void createEmployee(DataSource ds, Employee employee, final boolean useJta) throws Exception
    {
        // Instances of JDBC objects
        Connection conn = null;
        PreparedStatement statement = null;
        
        try {

            /*
             * Setup the transaction, based on whether JTA has been requested.
             */
            
            // The JTA transaction, if we're using one
            UserTransaction utx;
            
            // Transactions are started implictly in CICS, explicitly in JTA
            if ( useJta ) {
                // Get a new user transaction for this piece of work and start it
                utx = (UserTransaction) InitialContext.doLookup("java:comp/UserTransaction");
                utx.begin();
            }
            else {
                // A unit of work is already provided for this CICS transaction: no-op
                // Compiler not smart enough to work out utx won't be used again
                utx = null;
            }
            
            
            /*
             * Update the database.
             */
            
            // Our INSERT command for the DB
            String sqlCmd = "INSERT INTO EMP (" +
                                "BIRTHDATE, BONUS,    COMM, EDLEVEL,  EMPNO, " +
                                "FIRSTNME,  HIREDATE, JOB,  LASTNAME, MIDINIT, " +
                                "PHONENO,   SALARY,   SEX,  WORKDEPT) " +
                            "VALUES (" +
                                "?, ?, ?, ?, ?, " +
                                "?, ?, ?, ?, ?, " + 
                                "?, ?, ?, ?)";
            
            // Get the DB connection
            conn = ds.getConnection();
            
            // Prepare the statement and populate with data
            statement = conn.prepareStatement(sqlCmd);
            statement = populateStatement(employee, statement);
            
            // Perform the INSERT operation
            statement.executeUpdate();
            
            
            /*
             * Update a CICS resource.
             */
            
            // Update a TSQ, including it in the transaction
            TSQ tsq = new TSQ();
            tsq.setName(TSQ_NAME);
            String msg = String.format("Added %s with last name: %s", employee.getEmpno(), employee.getLastname());
            tsq.writeString(msg);

            
            /*
             * Commit the transaction.
             */
            
            // Handle JTA and non-JTA commits differently
            if ( useJta ) {
                // Use the JTA API to commit the changes
                utx.commit();
            }
            else {
                // Use the connection to commit the changes
                conn.commit();
            }
        }
        finally {
        
            // Any exceptions will be propagated
            
            // Close database objects, regardless of what happened
            if ( statement != null ) {
                statement.close();
            }
            if ( conn != null ) {
                conn.close();
            }
        }
    }
    
    /**
     * Deletes an employee from the database.
     * 
     * This method is called when a user presses the 'Delete' button next to an employee row.
     * 
     * It will use the employee number in the bean to fill in an delete statement
     * and remove the associated record from the DB.
     * 
     * @param ds - The target data source
     * @param employee - The employee object populated
     * @param useJta - use JTA to provide unit of work support, rather than CICS
     * 
     * @throws Exception
     */
    public static void deleteEmployee(DataSource ds, Employee employee, final boolean useJta) throws Exception
    {
        // Instances of JDBC objects
        Connection conn = null;
        PreparedStatement statement = null;
        
        try {

            /*
             * Setup the transaction, based on whether JTA has been requested.
             */
            
            // The JTA transaction, if we're using one
            UserTransaction utx;
            
            // Transactions are started implictly in CICS, explicitly in JTA
            if ( useJta ) {
                // Get a new user transaction for this piece of work and start it
                utx = (UserTransaction) InitialContext.doLookup("java:comp/UserTransaction");
                utx.begin();
            }
            else {
                // A unit of work is already provided for this CICS transaction: no-op
                // Compiler not smart enough to work out utx won't be used again
                utx = null;
            }
            
            
            /*
             * Update the database.
             */
            
            // Get the DB connection
            conn = ds.getConnection();
            
            // Prepare the statement and add the specified employee number
            statement = conn.prepareStatement("DELETE FROM EMP WHERE EMPNO = ?");
            statement.setString(1, employee.getEmpno());
            
            // Perform the DELETE operation
            statement.execute();


            /*
             * Update a CICS resource.
             */
            
            // Write some basic information about the deleted record to a TSQ
            TSQ tsq = new TSQ();
            tsq.setName(TSQ_NAME);
            String msg = String.format("Deleted %s with last name: %s", employee.getEmpno(), employee.getLastname());
            tsq.writeString(msg);

            
            /*
             * Commit the transaction.
             */
            
            // Handle JTA and non-JTA commits differently
            if ( useJta ) {
                // Use the JTA API to commit the changes
                utx.commit();
            }
            else {
                // Use the connection to commit the changes
                conn.commit();
            }
        }
        finally {
            
            // Any exceptions will be propagated
            
            // Close database objects, regardless of what happened
            if ( statement != null ) {
                statement.close();
            }
            if ( conn != null ) {
                conn.close();
            }
        }
    }
    
    /**
     * Uses connection.commit to commit a update an employee in the database.
     * 
     * This method is called when a user presses the 'Edit' then 'Save' button
     * next to an employee row when the JTA toggle is false.
     * 
     * It will use the employee number in the bean to fill in an UPDATE statement
     * and update the associated record from the DB
     * 
     * @param ds - The target data source
     * @param employee - The employee object populated
     * @throws Exception
     */
    public static void updateEmployee(DataSource ds, Employee employee, final boolean useJta) throws Exception
    {
        // Instances of JDBC objects
        Connection conn = null;
        PreparedStatement statement = null;
        
        try {

            /*
             * Setup the transaction, based on whether JTA has been requested.
             */
            
            // The JTA transaction, if we're using one
            UserTransaction utx;
            
            // Transactions are started implictly in CICS, explicitly in JTA
            if ( useJta ) {
                // Get a new user transaction for this piece of work and start it
                utx = (UserTransaction) InitialContext.doLookup("java:comp/UserTransaction");
                utx.begin();
            }
            else {
                // A unit of work is already provided for this CICS transaction: no-op
                // Compiler not smart enough to work out utx won't be used again
                utx = null;
            }

            
            /*
             * Clean some of the data before passing to the database.
             */

            // Uppercase the gender
            employee.setSex(employee.getSex().toUpperCase());

            
            /*
             * Update the database.
             */
            
            // The update command template used for the operation
            String sqlCmd = "UPDATE EMP SET " +
                                "BIRTHDATE = ?, BONUS = ?, COMM = ?, EDLEVEL = ?, EMPNO = ?, " +
                                "FIRSTNME = ?, HIREDATE = ?, JOB = ?, LASTNAME = ?, MIDINIT = ?, " +
                                "PHONENO = ?, SALARY = ?, SEX = ?, WORKDEPT = ? " +
                            "WHERE EMPNO = ?";

            // Get the DB connection
            conn = ds.getConnection();

            // Prepare the statement and populate with data
            statement = conn.prepareStatement(sqlCmd);
            populateStatement(employee, statement);
            statement.setString(15, employee.getEmpno());
            
            // Perform the UPDATE operation
            statement.execute();


            /*
             * Update a CICS resource.
             */
            
            // Write some basic information about the updated record to a TSQ
            TSQ tsq = new TSQ();
            tsq.setName(TSQ_NAME);
            String msg = String.format("Updated %s with last name: %s", employee.getEmpno(), employee.getLastname());
            tsq.writeString(msg);

            
            /*
             * Commit the transaction.
             */
            
            // Handle JTA and non-JTA commits differently
            if ( useJta ) {
                // Use the JTA API to commit the changes
                utx.commit();
            }
            else {
                // Use the connection to commit the changes
                conn.commit();
            }
        }
        finally {
            
            // Any exceptions will be propagated
            
            // Close database objects, regardless of what happened
            if ( statement != null ) {
                statement.close();
            }
            if ( conn != null ) {
                conn.close();
            }
        }
    }

    
    /**
     * Takes a ResultSet with a set pointer, and extracts
     * the Employee information from the row, storing it in a
     * new Employee bean.
     * 
     * @param currentResult - ResultSet with pointer
     * 
     * @return - Populated Employee bean
     */
    private static Employee createEmployeeBean(ResultSet currentResult) throws SQLException
    {
        Employee employee = new Employee();
        
        // Gather the employee information from the DB and set up the bean
        employee.setBirthdate(currentResult.getDate("BIRTHDATE"));
        employee.setBonus(currentResult.getBigDecimal("BONUS"));
        employee.setComm(currentResult.getBigDecimal("COMM"));
        employee.setEdlevel(currentResult.getObject("EDLEVEL") == null ? 0 : (short)currentResult.getShort("EDLEVEL"));
        employee.setEmpno(currentResult.getString("EMPNO"));
        employee.setFirstname(currentResult.getString("FIRSTNME"));
        employee.setHireDate(currentResult.getDate("HIREDATE"));
        employee.setJob(currentResult.getString("JOB"));
        employee.setLastname(currentResult.getString("LASTNAME"));
        employee.setMidinit(currentResult.getString("MIDINIT"));
        employee.setPhoneno(currentResult.getString("PHONENO"));
        employee.setSalary(currentResult.getBigDecimal("SALARY"));
        employee.setSex(currentResult.getString("SEX"));
        
        
        return employee;
    }
    
    
    /**
     * Populates a CREATE statement with values, taken from an employee bean.
     * 
     * @param employee - The employee you wish to use values from
     * @param statement - The statement you want to populate
     * @return - A populated statement
     * @throws SQLException
     */
    private static PreparedStatement populateStatement(Employee employee, PreparedStatement statement) throws SQLException
    {
        // Set a null department, as not set for the application
        String deptno = null;
    
        // Check for the values on date fields.
        Date bDate = employee.getBirthdate() == null ? null : (new Date(employee.getBirthdate().getTime()));
        Date hDate = employee.getHireDate() == null ? null : (new Date(employee.getHireDate().getTime()));
        
        // Fill in the rest of the fields
        statement.setDate(1, bDate);
        statement.setBigDecimal(2, employee.getBonus());
        statement.setBigDecimal(3, employee.getComm());
        statement.setShort(4, employee.getEdlevel());
        statement.setString(5, employee.getEmpno());
        statement.setString(6,  employee.getFirstname());
        statement.setDate(7, hDate);
        statement.setString(8,  employee.getJob());
        statement.setString(9,  employee.getLastname());
        statement.setString(10,  employee.getMidinit());
        statement.setString(11,  employee.getPhoneno());
        statement.setBigDecimal(12, employee.getSalary());
        statement.setString(13,  employee.getSex());
        statement.setString(14,  deptno);
        
        return statement;
    }
}
