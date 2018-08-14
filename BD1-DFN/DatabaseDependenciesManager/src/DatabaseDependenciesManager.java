import java.sql.*;
import java.io.Console;
import java.util.ArrayList;

/**
* Class that allows the management of databases and their functional dependencies
*
* @author 	HUYLENBROECK Florent; BRIQUET Florian
*/
public class DatabaseDependenciesManager
{
	/**
	* sqlite related fields
	*/
	private static Connection connection = null;
	private static Statement statement = null;
	private static ResultSet result = null;
	private static Console console = System.console();
	
	/**
	* Retrieved data related fields
	*/
	private static int ndep = 0;
	private static ArrayList<String> table_name_list = new ArrayList<String>();
	private static ArrayList<String> table_dep_list = new ArrayList<String>();
	private static ArrayList<String> lhs_list = new ArrayList<String>();
	private static ArrayList<String> rhs_list = new ArrayList<String>();
	
	
	public static void main(String[] args)
	{
		if(console==null)
		{
			System.out.println("Can't access console");
			System.exit(1);
		}
		
		System.out.println("-------- Databases dependencies manager --------");
		System.out.println("Type 'help' for help");
		
		while(true)
		{
			String commandLine = console.readLine();
			String[] commandArgs = commandLine.split(" ");
			
			if(commandArgs[0].equals("exit") || commandArgs[0].equals("quit"))
			{
				System.out.println("bye");
				System.exit(0);
			}
			
			if(commandArgs[0].equals("help"))
			{
				System.out.println(""
									+"add\n"
									+"\tHelps adding a dependency\n"			
									+"connect [path]\n"
									+"\t Connects to a database.\n"
									+"delete\n"
									+"\tHelps deleteting a dependency\n"
									+"disconnect\n"
									+"\tDisconnects from current database\n"
									+"list [table]\n"
									+"\t Lists dependencies associated to current database.\n"
									+"quit\n"
									+"\t Quits application.\n"
									+"sql [command]\n"
									+"\t Execute an sql command, no output will be shown.\n"
									+"useless\n"
									+"\t finds useless dependencies and allows to delete them\n");
			}
			
			if(connection==null && !commandArgs[0].equals("connect"))
			{
				System.out.println("You must connect to a database first");
				continue;
			}
			
			switch(commandArgs[0])
			{	
				case "add":
					if(commandArgs.length==1)
					{
						try{
							if(table_name_list.size()>0)
							{
								String table = selectTable("Which table does the dependency affects ?",false);
								String lhs = selectAttributes("Which attribute(s) makes the left-hand side of the lhs->rhs dependency ?\nFor multiple arguments, separate them with spaces", table, true);
								String rhs = selectAttributes("Which attribute makes the right-hand side of the lhs->rhs dependency ?", table, false);
							
								writeDep(table,rhs,lhs);
								ndep++;
								table_dep_list.add(table);
								lhs_list.add(lhs);
								rhs_list.add(rhs);
							}
							else
							{
								System.out.println("No table to work with");
								break;
							}
						
						}catch(SQLException sqle){
						}
					}
					else
						System.out.println("Method 'add' requires no arguments");
					
					break;
					
				case "connect":
					if(commandArgs.length==2)
					{
						try{
							connect(commandArgs[1]);
						}catch(SQLException sqle){
							System.out.println("Can't connect");
							continue;
						}
						System.out.println("Connected");
					}
					else 
						System.out.println("Invalid number of arguments for method 'connect', please type 'help' to see the correct argumentation");
					break;
					
				
				case "delete":
					if(ndep>0)
					{
						try{
							String table = selectTable("Which table to delete a dependency from ?",false);
						
							System.out.println("Which dependency to delete ? Type number corresponding to the dependency");
							listDep(table,true);
						
							String dep = "";
							int nb = 0;
							boolean correctnb=false;
						
							while(!correctnb)
							{
								dep=console.readLine();
								try{
									nb = Integer.parseInt(dep);
									if(nb>0 && nb<=ndep)
										correctnb=true;
									else
									{
										correctnb=false;
										System.out.println("No dependency has that number, try again");
									}
								}catch(NumberFormatException nfe){
									System.out.println("Not a number, try again");
									nb=0;
								}
							}
							nb--;
							deleteDep(table_dep_list.remove(nb), lhs_list.remove(nb), rhs_list.remove(nb));
						
						}catch(SQLException sqle){ 
							sqle.printStackTrace();
						}
					}
					else
						System.out.println("No dependencies to delete");
					
					
					
					
					
					break;
				case "disconnect":
					if(commandArgs.length==1)
					{
						try{
							disconnect();
						}catch(SQLException sqle){
							System.out.println("Can't disconnect, exit application and try again");
							continue;
						}
						if(connection==null)
							System.out.println("Disconnected");
					}
					else 
						System.out.println("Method 'disconnect' requires no argument");
					
					break;
					
				case "list":
					try{
						String table = selectTable("Which table to show dependency from ? Leave blank for showing every dependencies",true);
						listDep(table,false);//TODO
					}catch(SQLException sqle){
						System.out.println("Error"); //TODO
						continue;
					}
					break;
					
				case "logical":
					
					String useless = "";
					do{
						useless = console.readLine("It is advised to delete useless dependencies first, abort ? (y/n) : ").toLowerCase();
					}while(!useless.equals("y") && !useless.equals("n"));
					
					if(useless.equals("y"))
					{
						continue;
					}
					
					findLogicalConsequences();
					break;
					
				case "sql":
					if(commandArgs.length>1)
					{
						try{
							executeSQL(commandLine.substring(commandLine.indexOf(" "),commandLine.length()));
							System.out.println("Applied");
						}catch(SQLException sqle){
							System.out.println("Unable to aplly SQL request");
						}
					}
					else
						System.out.println("Invalid number of arguments for method 'sql', please type 'help' to see the correct argumentation");
					break;
					
				case "useless":
					if(commandArgs.length==1)
					{	
						ArrayList<Integer> uselessDepIndex = new ArrayList<Integer>();
						try{
							 uselessDepIndex = findUselessDep();
						}catch(SQLException sqle){
							System.out.println("Error while figuring out useless dependencies");
							sqle.printStackTrace();
						}
						
						
						if(uselessDepIndex.size()==0)
						{
							System.out.println("No useless dependencies found");
							continue;
						}
						
						System.out.println("Useless dependencies found :");
						
						for(int i=0; i<uselessDepIndex.size(); i++)
						{
							System.out.println("> "+table_dep_list.get(uselessDepIndex.get(i))+" : "
													+lhs_list.get(uselessDepIndex.get(i))+" -> "
													+rhs_list.get(uselessDepIndex.get(i)));
						}
						
						String delete = "";
						do{
							delete = console.readLine("Do you want to delete them ? (y/n) : ").toLowerCase();
						}while(!delete.equals("y") && !delete.equals("n"));
						
						if(delete.equals("y"))
						{
							try{
								for(int i=uselessDepIndex.size()-1; i>=0; i--)
								{
									deleteDep(table_dep_list.remove((int)uselessDepIndex.get(i)),
												lhs_list.remove((int)uselessDepIndex.get(i)),
												rhs_list.remove((int)uselessDepIndex.get(i)));
								}
							}catch(SQLException sqle2){
								System.out.println("Couldn't delete dependencies");
								continue;
							}
							
							System.out.println("Useless dependencies deleted");
						}
					}
					else
						System.out.println("Invalid number of arguments for method 'useless', please type 'help' to see the correct argumentation");
						
					break;
					
				default:
					System.out.println("Unsupported operation, type 'help' to see a list of supported operations");
			}
			
		}
	}
	
	/**
	* Connects to a session using username and password.
	*
	* @param url 	String, url of the database
	*/
	private static void connect(String url)
	throws SQLException
	{
		if(connection!=null)
		{
			String confirm="";
			do{
				confirm = console.readLine("A connection is already established, close existent connection ? (y/n) : ").toLowerCase();
			}while(!confirm.equals("y") && !confirm.equals("n"));
			
			if(confirm.equals("y"))
				connection.close();
			else
			{
				System.out.println("Connection attempt aborted");
				return;
			}
		}
		
		connection = DriverManager.getConnection("jdbc:sqlite:"+url);
		retrieveData();
	}

	/**
	* Disconnects from current session
	*/
	private static void disconnect()
	throws SQLException
	{
		if(connection!=null)
		{
			connection.close();
			connection=null;
		}
	}	
	
	private static void retrieveData()
	throws SQLException
	{
		table_name_list.clear();
		table_dep_list.clear();
		lhs_list.clear();
		rhs_list.clear();
		
		checkFuncDepTable();
		statement=connection.createStatement();
		result = statement.executeQuery("SELECT * FROM FuncDep");
		
		while(result.next()) 
		{	
			table_dep_list.add(result.getString("table_name"));
			lhs_list.add(result.getString("lhs"));
			rhs_list.add(result.getString("rhs"));
		}
		
		ndep=table_dep_list.size();
		
		DatabaseMetaData dmd = connection.getMetaData();
		result = dmd.getTables(null, null, "%", null);
		
		while(result.next()) 
		{
			if(result.getString(3).equals("FuncDep"))
				continue;
			table_name_list.add(result.getString(3));
		}
	}
	
	/**
	* Checks if FuncDep relation exists in a database and creates it if it does not.
	*/
	private static void checkFuncDepTable()
	throws SQLException
	{
		statement = connection.createStatement();
		statement.execute("CREATE TABLE IF NOT EXISTS FuncDep("
							+ "table_name VARCHAR(255) not NULL,"
							+ "lhs VARCHAR(255) not NULL,"
							+ "rhs VARCHAR(255) not NULL)");
		if(statement!=null)
			statement.close();
	}
	
	/**
	* Adds a dependency (table, lhs, rhs) in the relation FuncDep.
	*
	* @param table 	String, table affected by the dependency to add
	* @param lhs 	String, left-hand element(s) of the dependency to add
	* @param rhs 	String, right-hand element of the dependency to add
	*/
	private static void writeDep(String table, String lhs, String rhs)
	throws SQLException
	{
		checkFuncDepTable();
		statement=connection.createStatement();
		statement.execute("INSERT INTO FuncDep VALUES('"+table+"','"+lhs+"','"+rhs+"')");
		
		System.out.println("Dependency '"+table+"' : '"+rhs+"' -> '"+lhs+"'  added"); //TODO idk why rhs and lhs prints the other one, had to switch them
		
		if(statement!=null)
			statement.close();
	}
	
	/*
	* Lists dependencies.
	*
	* @param table 		String, table 
	* @param enumerate 	boolean, allows to enumerate the entries of the FuncDep table
	*/
	private static void listDep(String table, boolean enumerate)
	throws SQLException
	{
		if(table.equals("") && table_dep_list.size()==0)
		{
			System.out.println("No dependencies");
			return;
		}
		if(!table.equals("") && !table_dep_list.contains(table))
		{
			System.out.println("No dependencies for table '"+table+"'");
			return;
		}
		for(int i=0; i<table_dep_list.size(); i++)
		{
			if(table_dep_list.get(i).equals(table) || table.equals(""))
			{
				if(enumerate)
					System.out.print("> "+(i+1)+". ");
				System.out.println(table_dep_list.get(i)+" : "+lhs_list.get(i)+" -> "+rhs_list.get(i));
			}
		}
	}
	
	/*
	* Deletes a dependency.
	*
	* @param table 	String, table affected by the dependency to delete
	* @param lhs 	String, left-hand element(s) of the dependency to delete
	* @param rhs 	String, right-hand element of the dependency to delete
	*/
	private static void deleteDep(String table, String lhs, String rhs)
	throws SQLException
	{
		checkFuncDepTable();
		statement=connection.createStatement();
		statement.executeUpdate("DELETE FROM FuncDep WHERE table_name='"+table+"' AND lhs='"+lhs+"' AND rhs='"+rhs+"'");
		
		System.out.println("Dependency '"+table+"' : '"+lhs+"' -> '"+rhs+"'  deleted");
	}
	
	/*
	* Finds unsatisfied dependencies and allows the user to see and/or delete them.
	*/
	private static void findUnsatisfiedDep()
	{
		//TODO
	}
	
	/*
	* Finds logical consequences and allows the user to see them.
	*/
	private static void findLogicalConsequences()
	{
		for(int i=0; i<rhs_list.size(); i++)
		{
			for(int j=0; j<lhs_list.size(); j++)
			{
				if(table_dep_list.get(i).equals(table_dep_list.get(j)) && rhs_list.get(i).equals(lhs_list.get(j)))
				{
					System.out.println("Dependency '"+table_dep_list.get(i)+"' : '"+lhs_list.get(i)+"' -> '"+rhs_list.get(j)+"'\n"
											+"Is a logical consequence of :\n"
											+"Dependency '"+table_dep_list.get(i)+"' : '"+lhs_list.get(i)+"' -> '"+rhs_list.get(i)+"'\n"
											+"and\n"
											+"Dependency '"+table_dep_list.get(j)+"' : '"+lhs_list.get(j)+"' -> '"+rhs_list.get(j)+"'\n");
				}
			}
		
		}
	}
	
	/*
	* Finds useless dependencies and allows to delete them.
	*/
	private static ArrayList<Integer> findUselessDep()
	throws SQLException
	{
		ArrayList<Integer> UselessDepIndex = new ArrayList<Integer>();
		ArrayList<String> column_names = new ArrayList<String>();
		
		for(int i=0; i<table_dep_list.size(); i++)
		{
			if(!table_name_list.contains(table_dep_list.get(i)))
				UselessDepIndex.add(i);
			else
			{
				column_names.clear();
				column_names = listAttributes(table_dep_list.get(i),false);
			
				if(!column_names.contains(lhs_list.get(i)) || !column_names.contains(rhs_list.get(i)))
					UselessDepIndex.add(i);
				else if(lhs_list.get(i).equals(rhs_list.get(i)))
					UselessDepIndex.add(i);
			}
		}
		
		return UselessDepIndex;
	}
	
	/*
	* Lists every key or superkey for every relation.
	*/
	private static void findKeys()
	{
		//TODO
	}
	
	/*
	* Checks if the database respects BCNF normalisation. If not, finds the relations that do not respect the normalisation.
	*/
	private static boolean isBCNF()
	{
		//TODO
		return false;
	}
	
	/*
	* Checks if the database respects 3NF normalisation. If not, finds the relations that do not respect the normalisation.
	*/
	private static boolean is3NF()
	{
		//TODO
		return false;
	}
	
	/*
	*
	*/
	private static void exportTo3NF()
	{
		//TODO
	}
	
	/*
	* Executes an SQL statement
	*
	* @arg statement 	String, SQL request to execute
	*/
	private static void executeSQL(String request)
	throws SQLException
	{
		statement = connection.createStatement();
		statement.execute(request);
		
		if(statement!=null)
			statement.close();
			
		retrieveData();
	}
	
	/**
	* Selects a table in the current database
	*
	* @param message 	String, message to display before selecting the table
	* @param allowNone 	boolean, allows to chose no table
	* @return 			String, name of the selected table
	*/
	private static String selectTable(String message, boolean allowNone)
	throws SQLException
	{
		System.out.println(message);
		
		for(String table_name : table_name_list)
		{
			System.out.println("> "+table_name);
		}
		
		String table = console.readLine();
		
		while(!table_name_list.contains(table) && !(allowNone && table.equals("")))
		{
			table = console.readLine("No such table, try again : ");
		}
		
		System.out.println("Table '"+table+"' selected");
		return table;
	}
	
	/**
	* Selects some attributes of a given table
	*
	* @param message 	String, message to display before selecting attributes
	* @param table 		String, name of the table where to attributes are selected
	* @param multiple 	boolean, allows to select multiple attributes
	* @return 			String, all the selected attributes separated by a space
	*/
	private static String selectAttributes(String message, String table, boolean multiple)
	throws SQLException
	{
		System.out.println(message);
		
		ArrayList<String> column_names = listAttributes(table, true);
	
		String attr = console.readLine();
		String[] attrArgs = attr.split(" ");
	
		while(true)
		{
			if(!multiple && attrArgs.length>1)
			{
				System.out.println("Only one argument allowed, try again");
				continue;
			}
				
			for(String attrArg : attrArgs)
			{
				if(!column_names.contains(attrArg))
				{
					attr = console.readLine("Table '"+table+"' does not contain attribute '"+attrArg+"', try again\n");
					attrArgs = attr.split(" ");
					continue;
				}
			}
			System.out.println("Attribute(s) '"+attr+"' selected");
			return attr;
		}
	}
	
	/**
	* Lists attribute of a given table
	*
	* @param table 	String, name of the table
	* @param print 	boolean, allows to print the attributes
	* @return 		ArrayList<String> containing the attributes' name 
	*/
	private static ArrayList<String> listAttributes(String table, boolean print)
	throws SQLException
	{
		ArrayList<String> column_names = new ArrayList<String>();
							
		result = statement.executeQuery("SELECT * FROM "+table);
		ResultSetMetaData metadata = result.getMetaData();
	
		for (int i=1; i<=metadata.getColumnCount(); i++)
		{
			column_names.add(metadata.getColumnName(i));
			if(print)
				System.out.println("> "+metadata.getColumnName(i));
		}
		
		return column_names;
	}
}
