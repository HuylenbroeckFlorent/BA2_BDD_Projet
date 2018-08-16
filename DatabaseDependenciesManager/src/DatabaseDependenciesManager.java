import java.sql.*;
import java.io.Console;
import java.util.*;
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
	* Number of dependencies currently in the database
	*/
	private static int ndep = 0;

	/**
	* List containing the name of the table within the database
	*/
	private static ArrayList<String> table_name_list = new ArrayList<String>();

	/**
	* List containing the table affected by each dependencies
	*/
	private static ArrayList<String> table_dep_list = new ArrayList<String>();

	/**
	* List containing the left-hand side of every dependencies
	*/
	private static ArrayList<String> lhs_list = new ArrayList<String>();

	/**
	* List containing the right-hand side of every dependencies
	*/
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

			if(commandArgs[0].equals("") && connection==null){try{connect("./test_DB/test.db");}catch(SQLException sqle){sqle.printStackTrace();}continue;}//testing purpose

			if(commandArgs[0].equals(""))
			{
				continue;
			}

			else if(commandArgs[0].equals("exit") || commandArgs[0].equals("quit"))
			{
				System.out.println("bye");
				System.exit(0);
			}

			else if(commandArgs[0].equals("help"))
			{
				System.out.println(""
									+"add\n"
									+"\tHelps adding a dependency\n"
									+"connect [path]\n"
									+"\tConnects to a database.\n"
									+"delete\n"
									+"\tHelps deleteting a dependency\n"
									+"disconnect\n"
									+"\tDisconnects from current database\n"
									+"keys\n"
									+"\tFinds keys and checks BCNF/3NF normalisation\n"
									+"list\n"
									+"\tLists dependencies associated to current database.\n"
									+"quit\n"
									+"\tQuits application.\n"
									+"sql [command]\n"
									+"\tExecute an sql command, no output will be shown.\n"
									+"uns\n"
									+"\tFinds unsatisfied dependencies and allows to delete them or the bad tuples.\n"
									+"useless\n"
									+"\tFinds useless dependencies and allows to delete them.");
			}

			else if(connection==null && !commandArgs[0].equals("connect"))
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
								String lhs = selectAttributes("Which attribute(s) makes the left-hand side of the lhs->rhs dependency ?\nFor multiple arguments, use spaces", table, true);
								String rhs = selectAttributes("Which attribute makes the right-hand side of the lhs->rhs dependency ?", table, false);

								addDep(table,lhs,rhs);
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
							sqle.printStackTrace();
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

							int[] dep=selectDepNumber(table, true, "Which dependency to delete ?\nFor multiple arguments, use spaces");
							if(dep.length==0)
							{
								System.out.println("Aborted");
								continue;
							}
							for(int i=dep.length-1; i>=0; i--)
							{
								deleteDep(dep[i]-1);
							}

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

				case "keys":
					findKeys();
					break;

				case "list":
					try{
						String table = selectTable("Which table to show dependency from ? Leave blank to show every dependencies",true);
						listDep(table,false);//TODO
					}catch(SQLException sqle){
						System.out.println("Error"); //TODO
						continue;
					}
					break;

				case "logical":

					String useless=readYesOrNo("It is advised to delete useless dependencies first, abort ? (y/n) : ");

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
							sqle.printStackTrace();
							System.out.println("Unable to aplly SQL request");
						}
					}
					else
						System.out.println("Invalid number of arguments for method 'sql', please type 'help' to see the correct argumentation");
					break;

				case "uns":
					if(commandArgs.length==1)
					{
						findUnsatisfiedDep();
					}
					else
						System.out.println("Invalid number of arguments for method 'uns', please type 'help' to see the correct argumentation");

					break;

				case "useless":
					if(commandArgs.length==1)
					{
						findUselessDep();
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
			String confirm=readYesOrNo("A connection is already established, close existent connection ? (y/n) : ");

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

	/**
	* Retrieve data from the database's FuncDep relation and reads table names
	*/
	private static void retrieveData()
	throws SQLException
	{
		table_name_list.clear();
		table_dep_list.clear();
		lhs_list.clear();
		rhs_list.clear();

		checkFuncDepTable();

		DatabaseMetaData dmd = connection.getMetaData();
		result = dmd.getTables(null, null, "%", null);

		while(result.next())
		{
			if(result.getString(3).equals("FuncDep"))
				continue;
			table_name_list.add(result.getString(3));
		}

		statement=connection.createStatement();
		result = statement.executeQuery("SELECT * FROM FuncDep");

		while(result.next())
		{
			if(table_name_list.contains(result.getString("table_name")))
			{
				table_dep_list.add(result.getString("table_name"));
				lhs_list.add(result.getString("lhs"));
				rhs_list.add(result.getString("rhs"));
			}
			else
			{
				deleteDep(result.getString("table_name"),result.getString("lhs"),result.getString("rhs"));
			}
		}

		ndep=table_dep_list.size();
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
	* @param lhs 	String, left-hand element(s) of the dependency to add (can describe multiple elements)
	* @param rhs 	String, right-hand element of the dependency to add
	*/
	private static void addDep(String table, String lhs, String rhs)
	throws SQLException
	{
		checkFuncDepTable();
		statement=connection.createStatement();

		statement.execute("INSERT INTO FuncDep VALUES('"+table+"','"+lhs+"','"+rhs+"')");
		ndep++;

		table_dep_list.add(table);
		lhs_list.add(lhs);
		rhs_list.add(rhs);

		System.out.println("Dependency '"+table+"' : '"+lhs+"' -> '"+rhs+"'  added");

		if(statement!=null)
			statement.close();
	}

	/**
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
					System.out.print("("+(i+1)+") ");
				System.out.println(table_dep_list.get(i)+" : "+lhs_list.get(i)+" -> "+rhs_list.get(i));
			}
		}
	}

	/**
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

	/**
	* Deletes a dependency using its index
	*
	* @param i 	int, index of the dependency
	*/

	private static void deleteDep(int i)
	throws SQLException
	{
		checkFuncDepTable();
		statement=connection.createStatement();
		statement.executeUpdate("DELETE FROM FuncDep WHERE table_name='"+table_dep_list.get(i)+"' AND lhs='"+lhs_list.get(i)+"' AND rhs='"+rhs_list.get(i)+"'");
		System.out.println("Dependency '"+table_dep_list.get(i)+"' : '"+lhs_list.get(i)+"' -> '"+rhs_list.get(i)+"'  deleted");
		table_dep_list.remove(i);
		lhs_list.remove(i);
		rhs_list.remove(i);
	}

	/**
	* Finds unsatisfied dependencies and allows the user to see and/or delete them.
	*/
	private static void findUnsatisfiedDep()
	{
		boolean found=false;

		for(String table : table_name_list)
		{
			if(table.equals("FuncDep"))
				continue;

			for(int i=table_dep_list.size()-1; i>=0; i--)
			{
				String attributes="";
				String condition="";
				String tuple="";
				String lhs="";
				String rhs="";

				if(table_dep_list.get(i).equals(table))
				{
					attributes="";
					condition="WHERE ";
					tuple="(";

					lhs=lhs_list.get(i);
					rhs=rhs_list.get(i);

					for(String uniqueLHS : lhs.split(" "))
					{
						attributes+= ("A."+uniqueLHS+", ");
						condition+= ("A."+uniqueLHS+" = B."+uniqueLHS+" AND ");
						tuple+= (uniqueLHS+",");
					}

					attributes+= ("A."+rhs);
					condition+= ("A."+rhs+" <> B."+rhs);
					tuple+= (rhs+")");
				}
				//System.out.println("SELECT "+attributes+" FROM "+table+" A,"+table+" B "+condition);
				String query = "SELECT "+attributes+" FROM "+table+" A,"+table+" B "+condition;
				boolean result_not_empty=false;

				try
				{
					statement = connection.createStatement();
					result = statement.executeQuery(query);

					ResultSetMetaData rsmd = result.getMetaData();
					int columnsNumber = rsmd.getColumnCount();

					while (result.next())
					{
						if(!result_not_empty)
						{
							result_not_empty=true;
							found=true;
							System.out.println("\nTuples :");
						}

						System.out.print("(");
						for(int j=1; j<=columnsNumber; j++)
						{
							if(j>1 && j<columnsNumber)
							{
								System.out.print(", ");
							}
							else if(j==columnsNumber)
							{
								System.out.print(" -> ");
							}
							System.out.print(rsmd.getColumnName(j)+":"+result.getString(j));
						}
						System.out.println(")");
					}

				}catch (SQLException sqle){
					sqle.printStackTrace();
				}

				if(result_not_empty)
				{
					String delete =readYesOrNo("from table '"+table+"' do not satisfy the dependency '"+lhs_list.get(i)+" -> "+rhs_list.get(i)+"', delete them ? (y/n)");

					if(delete.equals("y"))
					{
						String deleteQuery=("DELETE FROM "+table+" WHERE "+tuple+" IN ("+query+");");
						//System.out.println(deleteQuery);
						try{
							statement.execute(deleteQuery);
							System.out.println("Row(s) deleted");
						}catch(SQLException sqle){
							System.out.println("Unable to delete row(s)");
						}
					}
					else
					{
						delete = readYesOrNo("Delete dependency ? (y/n)");

						if(delete.equals("y"))
						{
							try
							{
								deleteDep(i);
							}catch(SQLException sqle){
								System.out.println("Unable to delete dependency");
							}
						}
					}
					result_not_empty=false;
				}
			}
		}
		if(!found)
		{
			System.out.println("No unsatisfied dependencies found");
		}
	}

	/**
	* Finds logical consequences and allows the user to see them.
	*/
	private static void findLogicalConsequences()
	{
		
	}

	/**
	* Finds useless dependencies and allows to delete them.
	*/
	private static void findUselessDep()
	{
		boolean found=false;

		for(int i=table_dep_list.size()-1; i>=0; i--)
		{
			String lhs = lhs_list.get(i);
			String rhs = rhs_list.get(i);

			for(String uniqueLHS : lhs.split(" "))
			{
				if(rhs.equals(uniqueLHS))
				{
					found=true;
					String delete = readYesOrNo("Useless dependency found: '"+table_dep_list.get(i)+"' : '"+lhs+"' -> '"+rhs+"', delete it ? (y/n)");
					if(delete.equals("y"))
					{
						try
						{
							deleteDep(i);
						}catch(SQLException sqle){
							System.out.println("Unable to delete dependency");
						}
						break;
					}

				}
			}
		}

		if(!found)
			System.out.println("No useless dependency found");
	}

	/**
	* Lists every key or superkey for every relation and check for BCNF an 3NF compliancy.
	*/
	private static void findKeys()
	{
		ArrayList done = new ArrayList();
		for (String table : table_dep_list){
			if(!done.contains(table)){
				done.add(table);
				System.out.println("---" + table + "---\n");
				ArrayList<String> attributes = new ArrayList();
				ArrayList<String> nlnr = new ArrayList(); // Not left not right
				ArrayList<String> onlyR = new ArrayList(); // Only right
				ArrayList<String> onlyL = new ArrayList(); // Only left
				ArrayList<String> leftFD = new ArrayList();
				ArrayList<String> rightFD = new ArrayList();
				ArrayList<ArrayList> keys = new ArrayList();
				try{
					attributes = listAttributes(table);
				}catch(SQLException sqle){
					sqle.printStackTrace();
					System.out.println("Error while listing '"+table+"' attributes");
					continue;
				}
				Collections.sort(attributes);
				System.out.println("Complete attribute set: " + attributes);
				nlnr.addAll(attributes);
				for(int i=0; i<table_dep_list.size(); i++)
				{
					if(table_dep_list.get(i).equals(table)) //make nlnr array with attributes not on left nor on right
					{
						leftFD.add(lhs_list.get(i));
						for(String f : lhs_list.get(i).split(" ")){
							onlyL.add(f);
							removeDuplicate(onlyL);
						}
					}
					if(table_dep_list.get(i).equals(table)){
						rightFD.add(rhs_list.get(i));
						onlyR.add(rhs_list.get(i));
						removeDuplicate(onlyR);
					}
				}
				for(int i=0; i<table_dep_list.size(); i++) //Now we remove
				{
					if(table_dep_list.get(i).equals(table))
					{
						for(String f : lhs_list.get(i).split(" "))
						{
							nlnr.remove(f);
						}
					}

					if(table_dep_list.get(i).equals(table))
					{
						nlnr.remove(rhs_list.get(i));
						onlyL.remove(rhs_list.get(i));
					}

					if(table_dep_list.get(i).equals(table))
					{
						for(String f : lhs_list.get(i).split(" "))
						{
							onlyR.remove(f);
						}
					}
				}

				removeDuplicate(nlnr);
				removeDuplicate(onlyR);
				removeDuplicate(onlyL);

				Collections.sort(nlnr);
				Collections.sort(onlyR);
				Collections.sort(onlyL);

				System.out.println("Step 1 - Attribute(s) not on the left-hand side nor on the right-hand side : " + nlnr);
				System.out.println("Step 2 - Attribute(s) only on the right-hand side : " +onlyR);
				System.out.println("Step 3 - Attribute(s) only on the left-hand side : " +onlyL);
				ArrayList<String> step4 = new ArrayList();
				step4.addAll(nlnr);
				step4.addAll(onlyL);
				removeDuplicate(step4);
				Collections.sort(step4);
				System.out.println("Step 4 - Combine attribute(s) from step 1 and 3: " + step4);
				ArrayList<String> step5 = closure(step4, table);
				removeDuplicate(step5);
				Collections.sort(step5);
				System.out.println("Step 5 - Closure of the attribute(s) from step 4 : " + step5);
				if(step5.equals(attributes)){
					keys.add(step4);
					System.out.println("Step 6 - Closure of step 5 gives us all attribute --> keys : " + keys);
				}
				else{
					ArrayList<String> step6 = new ArrayList();
					step6.addAll(attributes);
					for(String attr : step4){
						step6.remove(attr);
					}
					for(String attr : onlyR){
						step6.remove(attr);
					}
					System.out.println("Step 6 - Find attribute(s) not included in step 4 and 2 : " + step6);
					System.out.println("Step 7 - Test closures of attribute(s) from step 4, plus one attribute from step 6 at a time :");
					ArrayList<String> toTest = new ArrayList();
					toTest.addAll(step4);
					boolean candidateFound = false;
					int attrToAdd = 1;
					while(!candidateFound){
						while(attrToAdd <= step6.size() && !candidateFound){
							String[] data = new String[step6.size()];
							ArrayList<String> data2 = new ArrayList<String>();
							String[] temp = step6.toArray(new String[step6.size()]);

							combinationUtil(temp,data,0,data.length-1,0,attrToAdd, data2);
							for(String toAdd: data2){
								for(String temp1 : toAdd.split("")){
									toTest.add(temp1);
								}
								ArrayList<String> res = closure(toTest,table);
								removeDuplicate(res);
								Collections.sort(res);
								if(res.equals(attributes)){
									ArrayList<String> copy = new ArrayList();
									copy.addAll(toTest);
									Collections.sort(copy);
									keys.add(copy);
									System.out.println("\t- Key found : " + copy);
									candidateFound = true;
								}
								toTest.clear();
								toTest.addAll(step4);
							}
							attrToAdd++;

						}
					}
				}
				removeDuplicate(keys);
				System.out.println("\nThe candidate keys are : " + keys+"\n");
				System.out.println("--- BCNF ---\n" + isBCNF(leftFD,keys)+"\n");
				System.out.println("--- 3NF ---\n" + is3NF(leftFD,rightFD,keys)+"\n");
			}
		}
	}
	/**
	* figures out every combination possible using this function recursively.
	*
	* @param arr 	String[], input array.
	* @param data 	String[], array used for the recursion.
	* @param start 	int, start index in arr.
	* @param end 	int, end index in arr.
	* @param index 	int, current index in data.
	* @param r 		int, size of a combination to be saved in res.
	* @param res 	ArrayList<String>, ArrayList where the result is going to be saved.
	*
	*/
	private static void combinationUtil(String[] arr, String[] data, int start,
                                int end, int index, int r, ArrayList<String> res)
    {
        // Current combination is ready to be printed, print it
				if (index == r)
				{
					String temp = "";
					for (int j=0; j<r; j++)
					{
						temp += data[j];
					}
					res.add(temp);
					temp = "";
					return;
				}

				// replace index with all possible elements. The condition
				// "end-i+1 >= r-index" makes sure that including one element
				// at index will make a combination with remaining elements
				// at remaining positions
				for (int i=start; i<=end && end-i+1 >= r-index; i++)
				{
					data[index] = arr[i];
					combinationUtil(arr, data, i+1, end, index+1, r, res);
				}
			}
 
	/**
	* Figures out the closure of a given table for a given set of attribute
	*
	* @param attributes 	ArrayList<String>, the set of attribute
	* @param table 			String, name of the given table
	* @return 				ArrayList<String>, closure
	*/
	private static ArrayList<String> closure(ArrayList<String> attributes, String table)
	{
		ArrayList<String> unusedLeft = new ArrayList<String>();
		ArrayList<String> unusedRight = new ArrayList<String>();

		ArrayList<String> closure = new ArrayList<String>();
		closure.addAll(attributes);

		for(int i=0; i<table_dep_list.size(); i++)
		{
			if(table_dep_list.get(i).equals(table))
			{
				unusedLeft.add(lhs_list.get(i));
				unusedRight.add(rhs_list.get(i));
			}
		}

		ArrayList<String> closure_copy = new ArrayList<String>();

		do{
			closure_copy.clear();
			closure_copy.addAll(closure);

			boolean containsLHS=true;
			
			for(int i=0; i<unusedRight.size(); i++)
			{
				for(String uniqueLHS : unusedLeft.get(i).split(" "))
				{
					containsLHS = containsLHS && attributes.contains(uniqueLHS);
				}

				if(containsLHS)
				{
					closure.add(unusedRight.get(i));
					removeDuplicate(closure);
				}
				else
				{
					containsLHS=true;
				}
			}

			Collections.sort(closure);
			Collections.sort(closure_copy);

		}while(!(closure.equals(closure_copy)));

		return closure;
	}


	/**
	* Remove duplicates in the given Arraylist.
	*
	* @param input 	 ArrayList, given ArrayList.
	*/
	private static void removeDuplicate(ArrayList input)
	{
		Set<String> noDuplicate = new LinkedHashSet<String>(input);
		input.clear();
		input.addAll(noDuplicate);
	}

	/**
	* Checks if the database respects BCNF normalisation.
	*
	* @param leftFD 	ArrayList<String>, left part of the functional dependencies.
	* @param keys 		ArrayList, keys of the concerned table.
	* @return 			boolean, true if the table is BCNF compliant.
	*/
	private static boolean isBCNF(ArrayList<String> leftFD, ArrayList keys)
	{
		for(String toTest : leftFD)
		{
			ArrayList<String> temp = new ArrayList();
			for(String temp1 : toTest.split(" "))
			{
				temp.add(temp1);
			}
			if(!keys.contains(temp))
				return false;
		}
		return true;
	}

	/**
	* Checks if the database respects 3NF normalisation.
	*
	* @param leftFD 	ArrayList<String>, left part of the functional dependencies.
	* @param rightFD 	ArrayList<String>, right part of the functional dependencies.
	* @param keys 		ArrayList, keys of the concerned table.
	* @return 			boolean, true if the table is 3NF compliant.
	*/
	private static boolean is3NF(ArrayList<String> leftFD,ArrayList<String> rightFD, ArrayList<ArrayList> keys)
	{
		for(int i = 0;i < leftFD.size(); i++)
		{
			ArrayList<String> temp2 = new ArrayList();
			for(String temp4 : leftFD.get(i).split(" "))
			{
				temp2.add(temp4);
			}
			if(!keys.contains(temp2))
			{
				boolean ok = false;
				for(ArrayList<String> key :  keys )
				{
					if(key.contains(rightFD.get(i)))
					{
						ok = true;
					}

				}
				if(!ok)
				{
					return false;
				}
			}
		}
		return true;
	}

	/**
	*
	*/
	private static void exportTo3NF()
	{
		//TODO
	}

	/**
	* Executes an SQL statement
	*
	* @param statement 	String, SQL request to execute
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
	* Selects a table in the current database using index
	*
	* @param message 	String, message to display before selecting the table
	* @param none 	boolean, allows to chose no table
	* @return 			String, name of the selected table
	*/
	private static String selectTable(String message, boolean none)
	throws SQLException
	{
		String table_name="";
		int nb=0;

		System.out.println(message);

		for(int i=0; i<table_name_list.size(); i++)
		{
			System.out.println("("+(i+1)+") "+table_name_list.get(i));
		}

		while(true)
		{
			String in = console.readLine();

			if(in.equals("") && none)
				break;

			try{
				nb = Integer.parseInt(in);
				if(nb>0 && nb<=table_name_list.size())
				{
					System.out.println("Table '"+table_name_list.get(nb-1)+"' selected");
					table_name = table_name_list.get(nb-1);
					break;
				}
				else
				{
					System.out.println("No table has that number, try again");
					continue;
				}
			}catch(NumberFormatException nfe){
				System.out.println(in+" is not a number, try again");
				continue;
			}
		}

		return table_name;
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
		String attributes = "";
		boolean done = false;

		System.out.println(message);

		ArrayList<String> table_attributes = listAttributes(table);

		for(int i=0; i<table_attributes.size(); i++)
		{
			System.out.println("("+(i+1)+") "+table_attributes.get(i));
		}

		while(!done)
		{
			attributes="";
			String in = console.readLine();

			if(in.equals(""))
			{
				continue;
			}

			String[] attr = in.split(" ");

			if(attr.length>1 && !multiple)
			{
				System.out.println("Only one argument allowed");
				continue;
			}

			int nb = 0;

			for(int i=0; i<attr.length; i++)
			{
				try{
					nb = Integer.parseInt(attr[i]);
					if(nb>0 && nb<=table_attributes.size())
					{
						attributes += table_attributes.get(nb-1);
						if(i==attr.length-1)
						{
							done=true;
							break;
						}
					}
					else
					{
						System.out.println("No attribute has that number, try again");
						continue;
					}
				}catch(NumberFormatException nfe){
					if(attr[i].equals(""))
						continue;
					System.out.println(attr[i]+" is not a number, try again");
					break;
				}

				if(!(attributes.equals("")))
				{
					attributes+=" ";
				}

			}
		}

		return attributes;
	}

	/**
	* Lists attribute of a given table
	*
	* @param table 	String, name of the table
	* @return 		ArrayList<String> containing the attributes' name
	*/
	private static ArrayList<String> listAttributes(String table)
	throws SQLException
	{
		ArrayList<String> attributes = new ArrayList<String>();

		result = statement.executeQuery("SELECT * FROM "+table);
		ResultSetMetaData metadata = result.getMetaData();

		for (int i=1; i<=metadata.getColumnCount(); i++)
		{
			attributes.add(metadata.getColumnName(i));
		}

		return attributes;
	}

	/**
	* Return a selection of dependencies using indexes.
	*
	* @param table 		String, name of the table
	* @param multiple 	Boolean, true allows for multiple selection
	* @param message 	String, message to be displayed before the selection
	* @return 			int[] containing the indexes
	*
	*/
	private static int[] selectDepNumber(String table, boolean multiple, String message)
	throws SQLException
	{
		ArrayList<Integer> depNumber = new ArrayList<Integer>();

		System.out.println(message);
		listDep(table, true);

		boolean correctnb=false;
		String dep = "";

		while(!correctnb)
		{
			depNumber = new ArrayList<Integer>();
			dep=console.readLine();
			if(!multiple && dep.split(" ").length>1)
			{
				System.out.println("Not allowed to input multiple arguments");
				continue;

			}
			for(String num : dep.split(" "))
			{
				int nb=0;

				try{
					nb = Integer.parseInt(num);
					if(nb>0 && nb<=ndep)
					{
						depNumber.add(nb);
					}
					else
					{
						System.out.println("No dependency has that number, try again");
						continue;
					}
				}catch(NumberFormatException nfe){
					System.out.println("Not a number, try again");
					continue;
				}
			}
			correctnb=true;
		}

		Collections.sort(depNumber);

		int[] ret = new int[depNumber.size()];

		for(int i=0; i<depNumber.size(); i++)
		{
			ret[i]=(int)depNumber.get(i);
		}

		return ret;
	}

	/**
	* Reads a console input until it's "y" or "n"
	*
	* @param message 	String, message to be displayed before reading the answer
	* @return 			String, answer to the message
	*/
	private static String readYesOrNo(String message)
	{
		String answer = console.readLine(message).toLowerCase();
		
		while(!answer.equals("y") && !answer.equals("n"))
		{
			answer = console.readLine("invalid answer").toLowerCase();
		}

		return answer;
	}
}