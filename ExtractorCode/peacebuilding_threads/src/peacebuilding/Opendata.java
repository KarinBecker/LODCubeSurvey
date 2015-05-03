package peacebuilding;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.http.ConnectionClosedException;
import org.openrdf.model.Statement;
import org.openrdf.model.Value;
import org.openrdf.query.BindingSet;
import org.openrdf.query.GraphQuery;
import org.openrdf.query.GraphQueryResult;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.QueryLanguage;
import org.openrdf.query.TupleQuery;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.repository.sparql.SPARQLRepository;
import org.openrdf.sail.memory.MemoryStore;

public class Opendata {
	static 	 File dataDir = new File("myrepository");
	static Repository localrepo = new SailRepository( new MemoryStore(dataDir) );
	static RepositoryConnection localCon=null;
	public static List<String> graphs=new LinkedList<String>();
	public static Map<String,String> linkAndEndpoints=new HashMap<String,String>();
	static String sparqlEndPoint="";
	static boolean verbose=true;
	static String prefixOpendata=""+
			"PREFIX dcterms: <http://purl.org/dc/terms/>\n"+
			"PREFIX owl: <http://www.w3.org/2002/07/owl#>\n"+
			"PREFIX qb: <http://purl.org/linked-data/cube#>\n"+
			"PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"+
			"PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"+
			"PREFIX skos: <http://www.w3.org/2004/02/skos/core#>\n"+
			"PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n";
	private static Object myLock = 1;
	
	static List<String> queryLinkedList= new LinkedList<String>();
	public static void main(String[]args) throws IOException, RepositoryException, QueryEvaluationException, MalformedQueryException{
		linkGraph();
		if (verbose)
			for (Map.Entry<String, String> entry: linkAndEndpoints.entrySet()){
				String endpoint=entry.getValue();
				String graph=entry.getKey();
				System.out.println(endpoint+"  ,  "+graph);
		}
		System.out.println("Map for endpoints and their graphs is created...");
		BufferedReader queryReader=new BufferedReader(new FileReader("FinalConstructQueries.txt"));
		String queryLine=queryReader.readLine();
		while (queryLine!=null){//read from the file of the queries and add them to a list--queryLinkedList
			String query="";
			while( queryLine!=null&&!queryLine.contains("#")){
				query+=queryLine+"\n";
				queryLine=queryReader.readLine();
			}
			queryLinkedList.add(query);
			queryLine=queryReader.readLine();
		}
		try{
		try {
			localrepo.initialize();
		} catch (RepositoryException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		try {
			localCon = localrepo.getConnection();

		} catch (RepositoryException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}
		
		final List<Integer> syncList = Collections.synchronizedList(new ArrayList<Integer>());
		final MyCount mctr = new MyCount();
		for (Map.Entry<String, String> entry: linkAndEndpoints.entrySet()){
			final Map.Entry<String, String> currEntry = entry;
			new Thread(new Runnable() {
				@Override
				public void run() {
					syncList.add(1);
					synchronized (myLock) {

						mctr.count++;
					}
					String endpoint=currEntry.getValue();
					String graph=currEntry.getKey();
					for(String li:queryLinkedList){
						String q="";
						if (li.length()>0){
						q=addGraphToQuery(li, graph);
						try {
							connection(endpoint, q);
						} catch (RepositoryException | QueryEvaluationException
								| MalformedQueryException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}}
					}
					syncList.remove(0);
				}
			}).start();
			
		}
		while(syncList.size() > 0 || mctr.count < linkAndEndpoints.entrySet().size())
		{
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		select();
		}finally{
			localCon.close();
			localrepo.shutDown();
			
		}
	}
	public static String addGraphToQuery(String query,String graph){
		String st="";
		int point=query.lastIndexOf("where{");
		if (point<0){
			point=query.lastIndexOf("WHERE{");
		}
		point+=6;
		if (point>6){
		st=query.substring(0, point);
		if(graph.startsWith("<"))
		st+="Graph"+graph+"{";
		else st+="Graph<"+graph+">{";
		st+=query.substring(point, query.length());
		st+="}";
		}
		return st;
	}
	public static void connection(String endpoint,String sp) throws RepositoryException, QueryEvaluationException, MalformedQueryException{
		sparqlEndPoint=endpoint;
		 Repository Remoterepo=new SPARQLRepository(sparqlEndPoint);
		RepositoryConnection con=null;
		
		String sparqlQuery=prefixOpendata+sp;
		System.out.println("Endpoint:"+sparqlEndPoint);
		System.out.println("Query: "+sparqlQuery);
		try {
			//localrepo.initialize();
			Remoterepo.initialize();
		} catch (RepositoryException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		try {
			//localCon = localrepo.getConnection();
			con = Remoterepo.getConnection();

		} catch (RepositoryException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}
		
		try{
		GraphQuery graphQuery= con.prepareGraphQuery(QueryLanguage.SPARQL, sparqlQuery);
		GraphQueryResult gresult;
		try {
			gresult = graphQuery.evaluate();
		
		try {

			while (gresult.hasNext()) {

				Statement statement = gresult.next();
				System.out.println(statement.toString());
				try {
					synchronized (myLock) {
						localCon.add(statement);
					}
				} catch (RepositoryException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		finally {
			try {
				gresult.close();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
			}
			//localCon.close();
			con.close();
		}
		} catch (QueryEvaluationException e1) {
			// TODO Auto-generated catch block
			//e1.printStackTrace();
		}
	}catch(RepositoryException e3){
		//e3.printStackTrace();
	}
		Remoterepo.shutDown();
	
}
	public static void select() throws RepositoryException, MalformedQueryException, QueryEvaluationException{
		TupleQuery tupleq10 = localCon.prepareTupleQuery(QueryLanguage.SPARQL, q10());
		TupleQueryResult resultq10 = tupleq10.evaluate();
		FileWriter fw = null;
		try {
			fw = new FileWriter(new File("out1.txt"));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		while (resultq10.hasNext()) {
			BindingSet bindingSet = resultq10.next();
			Value valueOfS = bindingSet.getValue("S");
			Value valueOfP = bindingSet.getValue("P");
			Value valueOfO = bindingSet.getValue("O");
			System.out.println(valueOfS+","+valueOfP+","+valueOfO);
			try {
				fw.write(valueOfS+","+valueOfP+","+valueOfO + "\n");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		try {
			fw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		//localCon.close();
		//con.close();
		/*try {
			localrepo.shutDown();
			//Remoterepo.shutDown();
		} catch (RepositoryException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/
		System.out.println("Done!!");
	}

	public static void linkGraph(){ 
		try(BufferedReader br = new BufferedReader(new FileReader("graph.txt"))) {
			String line = br.readLine();

			while (line != null) {
				graphs.add(line);
				linkAndEndpoints.put(line,"http://opendatacommunities.org/sparql");
				//System.out.println(line);
				line = br.readLine();
			}
			br.close();
		}catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try(BufferedReader br2 = new BufferedReader(new FileReader("endpointsandlinks.txt"))) {
			String line = br2.readLine();
			String line2=br2.readLine();
			while (line != null&&line2!=null) {
				linkAndEndpoints.put(line2,line);
				line = br2.readLine();
				line2=br2.readLine();
			}
			br2.close();
		}catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}


	public static String q10(){
		String res=""+
				"SELECT ?S ?P ?O\n"+
				"WHERE{\n"+
				"?S ?P ?O\n"+
				"}\n";
		return res;
	}
	static class MyCount
	{
		Integer count = 0;
	}
}
