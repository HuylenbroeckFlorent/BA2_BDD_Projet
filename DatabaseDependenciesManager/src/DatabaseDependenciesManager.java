import java.sql.*;
import java.io.Console;
import java.util.*;
/**
* Class that allows the management of databases and their functional dependencies
*
* @author 	HUYLENBROECK Florent; SAHLI Yacine
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

	/**
	* List containing the keys of every relation
	*/
	private static ArrayList<Key> keys = new ArrayList<Key>();

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

			if(commandArgs[0].equals(""))
			{
				continue;
			}

			else if(commandArgs[0].equals("exit") || commandArgs[0].equals("quit"))
			{
				System.out.println("Bye. o/");
				System.exit(0);
			}

			else if(commandArgs[0].equals("help"))
			{
				System.out.println(""
									+"add\n"
									+"\tHelps adding a dependency.\n"
									+"connect [path]\n"
									+"\tConnects to a database. Local databases can be created by this command.\n"
									+"delete\n"
									+"\tHelps deleteting a dependency.\n"
									+"disconnect\n"
									+"\tDisconnects from current database.\n"
									+"is3NF\n"
									+"\tChecks if tables recpect 3NF normalization.\n"
									+"isBCNF\n"
									+"\tChecks if tables respect BCNF normalization.\n"
									+"keys\n"
									+"\tShow keys.\n"
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
								String rhs = selectAttributes("Which attribute makes the right-hand side of the lhs->rhs dependency ? \nFor multiple arguments, use spaces (multiple arguments will be split into singular DF's)", table, true);

								for(String uniqueRHS : rhs.split(" "))
								{
									addDep(table,lhs,uniqueRHS);
								}
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
							System.out.println("Unable to select table");
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

				case "is3NF":
					if(commandArgs.length==1)
					{
						if(is3NF())
						{
							System.out.println("Database respects 3NF normalization");
						}
						break;
					}
					else
						System.out.println("Method 'is3NF' requires no arguments");

				case "isBCNF":
					if(commandArgs.length==1)
					{
						if(isBCNF())
						{
							System.out.println("Database respects BCNF normalization");
						}
						break;
					}
					else
						System.out.println("Method 'isBCNF' requires no arguments");

				case "keys":
					String tables = "";
					try
					{
						tables = selectTable("Which table to show the keys from ? Leave blank to show every key", true);
					}catch(SQLException sqle){
						System.out.println("Unable to select table");
					}

					ArrayList<String> selected_tables = new ArrayList<String>();

					if(tables.equals(""))
					{
						selected_tables.addAll(table_name_list);
					}
					else
					{
						selected_tables.add(tables);
					}

					boolean name_printed=false;
					boolean first=true;

					for(String table : selected_tables)
					{
						name_printed=false;
						for(Key key : keys)
						{
							if(key.getTable_name().equals(table))
							{
								if(!name_printed)
								{
									if(!first)
									{
										System.out.println();
									}
									first=false;
									System.out.println("Keys from table '"+table+"' :");
									name_printed=true;
								}
								System.out.println("\t"+key.getKey());
							}
						}
					}
					break;

				case "list":
					try{
						String table = selectTable("Which table to show dependency from ? Leave blank to show every dependencies",true);
						listDep(table,false);
					}catch(SQLException sqle){
						System.out.println("Error");
						continue;
					}
					break;

				case "logical":
					ArrayList<Integer> logical_consequences = findLogicalConsequences();
					if(logical_consequences.size()==0)
					{
						System.out.println("No logical consequence found");
						continue;
					}
					for(int i=logical_consequences.size()-1; i>=0; i--)
					{
						int j=logical_consequences.get(i);

						System.out.println("Dependency '"+table_dep_list.get(j)+"' : '"+lhs_list.get(j)+"' -> '"+rhs_list.get(j)+"' is a logical consequence");
					}
					break;

				case "sql":
					if(commandArgs.length>1)
					{
						try{
							executeSQL(commandLine.substring(commandLine.indexOf(" "),commandLine.length()));
							System.out.println("Applied");
						}catch(SQLException sqle){
							System.out.println("Unable to apply SQL request");
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
						ArrayList<Integer> useless_dependencies = findUselessDep();

						if(useless_dependencies.size()==0)
						{
							System.out.println("No useless dependencies found");
							continue;
						}

						for(int j=useless_dependencies.size()-1; j>=0; j--)
						{
							int i=useless_dependencies.get(j);
							System.out.println("Useless dependency found : '"+table_dep_list.get(i)+"' : '"+lhs_list.get(i)+"' -> '"+rhs_list.get(i)+"'. Delete it ? (y/n)");

							String delete = readYesOrNo("");
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

		ArrayList<String> useless_name_list = new ArrayList<String>();
		ArrayList<String> useless_lhs_list = new ArrayList<String>();
		ArrayList<String> useless_rhs_list = new ArrayList<String>();

		checkFuncDepTable();

		DatabaseMetaData dmd = connection.getMetaData();
		result = dmd.getTables(null, null, "%", null);

		while(result.next())
		{
			if(result.getString(3).equals("FuncDep"))
				continue;
			table_name_list.add(result.getString(3));
		}

		statement = connection.createStatement();
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
				useless_name_list.add(result.getString("table_name"));
				useless_lhs_list.add(result.getString("lhs"));
				useless_rhs_list.add(result.getString("rhs"));
			}
		}

		if(useless_name_list.size()>0)
		{
			for(int i=0; i<useless_name_list.size(); i++)
			{
				deleteDep(useless_name_list.get(i), useless_lhs_list.get(i), useless_rhs_list.get(i));
			}
		}

		ndep=table_dep_list.size();
		findKeys();
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

		findKeys();
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

		findKeys();
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
		findKeys();
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
				}

				if(result_not_empty)
				{
					String delete =readYesOrNo("from table '"+table+"' do not satisfy the dependency '"+lhs_list.get(i)+" -> "+rhs_list.get(i)+"', delete them ? (y/n)");

					if(delete.equals("y"))
					{
						String deleteQuery=("DELETE FROM "+table+" WHERE "+tuple+" IN ("+query+");");
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
	private static ArrayList<Integer> findLogicalConsequences()
	{
		ArrayList<Integer> logical_consequences = new ArrayList<Integer>();

		for(String table : table_name_list)
		{
			for(int i=0; i<table_dep_list.size(); i++)
			{
				if(table_dep_list.get(i).equals(table))
				{
					ArrayList<String> left_attributes_of_dep_i = new ArrayList<String>();

					for(String uniqueLHS : lhs_list.get(i).split(" "))
					{
						left_attributes_of_dep_i.add(uniqueLHS);
					}

					ArrayList<String> lhs_without_dep_i = new ArrayList<String>();
					ArrayList<String> rhs_without_dep_i = new ArrayList<String>();

					for(int j=0; j<lhs_list.size(); j++)
					{
						if(j==i || logical_consequences.contains(j))
						{
							continue;
						}
						if(table_dep_list.get(j).equals(table_dep_list.get(i)))
						{
							lhs_without_dep_i.add(lhs_list.get(j));
							rhs_without_dep_i.add(rhs_list.get(j));
						}
					}

					ArrayList<String> closure = closure(left_attributes_of_dep_i, lhs_without_dep_i, rhs_without_dep_i);

					if(closure.contains(rhs_list.get(i)))
					{
						logical_consequences.add(i);
					}
				}
			}
		}

		Collections.sort(logical_consequences);

		return logical_consequences;
	}

	/**
	* Finds useless dependencies and allows to delete them.
	*
	* @return 	ArrayList<Integer>, the indexes of the useless dependencies
	*/
	private static ArrayList<Integer> findUselessDep()
	{
		ArrayList<Integer> useless_dependencies = new ArrayList<Integer>();

		for(int i=table_dep_list.size()-1; i>=0; i--)
		{
			String lhs = lhs_list.get(i);
			String rhs = rhs_list.get(i);

			for(String uniqueLHS : lhs.split(" "))
			{
				if(rhs.equals(uniqueLHS))
				{
					useless_dependencies.add(i);
				}
			}

			for(int j=0; j<table_dep_list.size(); j++)
			{
				if(table_dep_list.get(i).equals(table_dep_list.get(j)) && !useless_dependencies.contains(j) && i!=j)
				{
					if(rhs_list.get(i).equals(rhs_list.get(j)))
					{
						ArrayList<String> i_lhs = new ArrayList<String>(Arrays.asList(lhs_list.get(i).split(" ")));
						ArrayList<String> j_lhs = new ArrayList<String>(Arrays.asList(lhs_list.get(j).split(" ")));

						if(i_lhs.containsAll(j_lhs))
						{
							useless_dependencies.add(i);
						}

					}
				}
			}
		}
		Collections.sort(useless_dependencies);

		return useless_dependencies;
	}

	/**
	* Lists every key or superkey for every relation and check for BCNF an 3NF compliancy.
	* For every table :
	* step 0) Retrieve the complete set of attribute.
	* step 1) Lists attributes not on the left-hand side nor on the right-hand side
	* step 2) Lists attributes only on the right-hand side
	* step 3) Lists attributes only on the left-hand side
	* step 4) Combines attributes from step 1 and 3
	* step 5) Find the closure of the attributes from step 4
	*    	  If step 5) returns the complete attribute set from step 0), a key is step 4)
	* step 6) Else, find attributes not included in step 2 and 4
	* step 7) Compute closure of attributes from step 4, plus all possible permutation of attribute from step 6
	*    	  If the closure equals step 0) and is minimal, the closure is then sorted and added to "keys"
	*/
	private static void findKeys()
	{
		ArrayList<String> done = new ArrayList<String>();

		keys = new ArrayList<Key>();

		// For every table
		for (String table : table_dep_list)
		{
			if(!done.contains(table))
			{
				done.add(table);
			}
			else
			{
				continue;
			}
			ArrayList<String> attributes = new ArrayList<String>();
			ArrayList<String> attributes_not_on_table_rhs_nor_on_table_lhs = new ArrayList<String>(); // Not left not right
			ArrayList<String> attributes_only_on_table_rhs = new ArrayList<String>(); // Only right
			ArrayList<String> attributes_only_on_table_lhs = new ArrayList<String>(); // Only left
			ArrayList<String> table_lhs = new ArrayList<String>();
			ArrayList<String> table_rhs = new ArrayList<String>();

			ArrayList<String> table_keys = new ArrayList<String>();

			// step 0) Retrieve the complete set of attribute.

			try
			{
				attributes = listAttributes(table);
			}catch(SQLException sqle){
				System.out.println("Error while listing '"+table+"' attributes");
				continue;
			}

			// step 1) Lists attributes not on the left-hand side nor on the right-hand side
			// step 2) Lists attributes only on the right-hand side
			// step 3) Lists attributes only on the left-hand side

			Collections.sort(attributes);
			attributes_not_on_table_rhs_nor_on_table_lhs.addAll(attributes);

			for(int i=0; i<table_dep_list.size(); i++)
			{
				if(table_dep_list.get(i).equals(table))
				{
					table_lhs.add(lhs_list.get(i));
					for(String f : lhs_list.get(i).split(" ")){
						attributes_only_on_table_lhs.add(f);
					}

					table_rhs.add(rhs_list.get(i));
					attributes_only_on_table_rhs.add(rhs_list.get(i));

					removeDuplicate(attributes_only_on_table_rhs);
					removeDuplicate(attributes_only_on_table_lhs);
					removeDuplicate(attributes_not_on_table_rhs_nor_on_table_lhs);
				}
			}
			for(int i=0; i<table_dep_list.size(); i++)
			{
				if(table_dep_list.get(i).equals(table))
				{
					for(String f : lhs_list.get(i).split(" "))
					{
						attributes_not_on_table_rhs_nor_on_table_lhs.remove(f);
						attributes_only_on_table_rhs.remove(f);
					}
					attributes_not_on_table_rhs_nor_on_table_lhs.remove(rhs_list.get(i));
					attributes_only_on_table_lhs.remove(rhs_list.get(i));
				}
			}

			Collections.sort(attributes_not_on_table_rhs_nor_on_table_lhs);
			Collections.sort(attributes_only_on_table_rhs);
			Collections.sort(attributes_only_on_table_lhs);

			// step 4) Combines attributes from step 1 and 3

			ArrayList<String> step4 = new ArrayList<String>();
			step4.addAll(attributes_not_on_table_rhs_nor_on_table_lhs);
			step4.addAll(attributes_only_on_table_lhs);

			removeDuplicate(step4);
			Collections.sort(step4);

			// step 5) Find the closure of the attributes from step 4

			ArrayList<String> step5 = closure(step4, table_lhs,table_rhs);

			removeDuplicate(step5);

			Collections.sort(step5);

			// If step 5) returns the complete attribute set from step 0), a key is step 4)

			if(step5.equals(attributes)){
				keys.add(new Key(table, step4));
			}

			// step 6) Else, find attributes not included in step 2 and 4

			else
			{
				ArrayList<String> step6 = new ArrayList<String>();
				step6.addAll(attributes);

				for(String attr : step4)
				{
					step6.remove(attr);
				}

				for(String attr : attributes_only_on_table_rhs)
				{
					step6.remove(attr);
				}

				// step 7) Compute closure of attributes from step 4, plus all possible permutation of attribute from step 6

				ArrayList<String> toTest = new ArrayList<String>();
				toTest.addAll(step4);

				int attrToAdd = 1;

				while(attrToAdd <= step6.size())
				{
					String[] data = new String[step6.size()];
					ArrayList<String> data2 = new ArrayList<String>();
					String[] temp = step6.toArray(new String[step6.size()]);

					combinationUtil(temp,data,0,data.length-1,0,attrToAdd, data2);

					for(String toAdd: data2)
					{
						for(String temp1 : toAdd.split(""))
						{
							toTest.add(temp1);
						}
						ArrayList<String> res = closure(toTest,table_lhs,table_rhs);
						removeDuplicate(res);
						Collections.sort(res);

						// If the closure equals step 0) and is minimal, the closure is then sorted and added to "table_keys"

						if(res.equals(attributes))
						{
							ArrayList<String> copy = new ArrayList<String>();
							copy.addAll(toTest);
							Collections.sort(copy);

							if(isMinimal(table, copy))
							{
								keys.add(new Key(table, copy));
							}
						}
						toTest.clear();
						toTest.addAll(step4);
					}
					attrToAdd++;

				}
			}
			removeDuplicate(keys);
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
	* @param lhs 			ArrayList<String>, list of the left-hand sides of the dependencies
	* @param rhs 			ArrayList<String>, list of the right-hand sides of the dependencies
	* @return 				ArrayList<String>, closure
	*/
	private static ArrayList<String> closure(ArrayList<String> attributes, ArrayList<String> lhs, ArrayList<String> rhs)
	{
		ArrayList<String> unusedLeft = new ArrayList<String>();
		ArrayList<String> unusedRight = new ArrayList<String>();

		unusedLeft.addAll(lhs);
		unusedRight.addAll(rhs);

		ArrayList<String> closure = new ArrayList<String>();
		closure.addAll(attributes);

		ArrayList<String> closure_copy = new ArrayList<String>();

		do{
			closure_copy.clear();
			closure_copy.addAll(closure);

			boolean containsLHS=true;

			for(int i=0; i<unusedRight.size(); i++)
			{
				for(String uniqueLHS : unusedLeft.get(i).split(" "))
				{
					containsLHS = containsLHS && closure.contains(uniqueLHS);
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
	* Checks if a key is minimal (ie no current key are totally included in the tested key)
	*
	* @param key 	ArrayList<String>, the key to test
	* @return 		boolean, true if the key is minimal
	*/
	private static boolean isMinimal(String table, ArrayList<String> key)
	{
		boolean minimal=true;

		for(Key minkey : keys)
		{
			if(minkey.getTable_name().equals(table))
			{
				minimal = minimal && !key.containsAll(minkey.getKey());
			}
		}
		return minimal;
	}


	/**
	* Remove duplicates in the given Arraylist.
	*
	* @param input 	 ArrayList, given ArrayList.
	*/
	private static <E extends Comparable> void removeDuplicate(ArrayList<E> input)
	{
		Set<E> noDuplicate = new LinkedHashSet<E>(input);
		input.clear();
		input.addAll(noDuplicate);
	}

	/**
	* Checks if the database respects BCNF normalisation.
	*
	* @param table 		String, the table to check the BCNF compliance
	* @return 			boolean, true if the table is BCNF compliant.
	*/
	private static boolean isBCNF()
	{
		boolean bcnf=true;

		for(String table : table_name_list)
		{
			for(int i=0; i<table_dep_list.size(); i++)
			{
				if(table_dep_list.get(i).equals(table))
				{
					ArrayList<String> unique_lhs_attribute = new ArrayList<String>();
					for(String unique_lhs : lhs_list.get(i).split(" "))
					{
						unique_lhs_attribute.add(unique_lhs);
					}

					if(!(isKey(table,unique_lhs_attribute)))
					{
						System.out.println("\tDependency '"+table+"' : '"+lhs_list.get(i)+"' -> '"+rhs_list.get(i)+"' fails BCNF compliance : '"+lhs_list.get(i)+"' is not a key");
						bcnf=false;
					}
					
				}
			}
		}
		return bcnf;
	}

	/**
	* Checks if the database respects 3NF normalisation.
	*
	* @return 			boolean, true if the table is 3NF compliant
	*/
	private static boolean is3NF()
	{
		boolean _3nf=true;

		for(String table : table_name_list)
		{
			_3nf = _3nf && is3NF(table);
		}

		return _3nf;
	}

	/**
	* Checks if a table respects 3NF normalisation.
	*
	* @param table 		String, the table to check the 3NF compliance
	* @return 			boolean, true if the table is 3NF compliant
	*/
	private static boolean is3NF(String table)
	{
		boolean _3nf=true;

		boolean lhs_is_key=true;
		boolean rhs_is_prime=true;

		for(int i=0; i<table_dep_list.size(); i++)
		{
			if(table_dep_list.get(i).equals(table))
			{
				ArrayList<String> unique_lhs_attribute = new ArrayList<String>();

				for(String unique_lhs : lhs_list.get(i).split(" "))
				{
					unique_lhs_attribute.add(unique_lhs);
				}

				lhs_is_key = isKey(table, unique_lhs_attribute);
				rhs_is_prime = isPrime(table, rhs_list.get(i));

				if(!(lhs_is_key || rhs_is_prime))
				{
					_3nf=false;

					if(!lhs_is_key)
					{
						System.out.println("\tDependency '"+table+"' : '"+lhs_list.get(i)+"' -> '"+rhs_list.get(i)+"' fails 3NF compliance : '"+lhs_list.get(i)+"' is not a key and '"+rhs_list.get(i)+"' is not prime");
					}
				}	
			}
		}

		return _3nf;
	}

	/**
	* Checks if an attribute is prime for the selected table
	*
	* @param table 			String, the table selected
	* @param attribute 		String, the attribute to test
	* @return 				boolean, true if 'attribute' is a key
	*/
	private static boolean isPrime(String table, String attribute)
	{
		boolean isKey = false;

		for(Key key : keys)
		{
			if(key.getTable_name().equals(table))
			{
				isKey = isKey || key.getKey().contains(attribute);
			}
		}
		return isKey;
	}

	/**
	* Checks if a set of attribute is a key for the selected table
	*
	* @param table 			String, the table selected
	* @param attributes 	Arraylist<String>, the set of attributes to test
	* @return 				boolean, true if 'attribute' is a key
	*/
	private static boolean isKey(String table, ArrayList<String> attributes)
	{
		boolean isKey = false;

		for(Key key : keys)
		{
			if(key.getTable_name().equals(table))
			{
				isKey = isKey || attributes.containsAll(key.getKey());
			}
		}
		return isKey;
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
		System.out.println();
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

class Key implements Comparable<Key>{
	private String table_name;
	private ArrayList<String> key;

	public Key(String table_name, ArrayList<String> key)
	{
		this.table_name=table_name;
		this.key=new ArrayList<String>();
		this.key.addAll(key);
	}

	public String getTable_name()
	{
		return table_name;
	}

	public ArrayList<String> getKey()
	{
		return key;
	}

	public int compareTo(Key other)
	{
		if(table_name.equals(other.getTable_name()) && key.equals(other.getKey()))
			return 0;
		else 
			return 1;
	}
}