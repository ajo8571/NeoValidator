package helpers;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;
import java.util.regex.Pattern;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;

public class GraphBuilder {

  public static boolean deleteDir(File dir) {
    if (dir.isDirectory()) {
      String[] children = dir.list();
      for (int i=0; i<children.length; i++) {
        boolean success = deleteDir(new File(dir, children[i]));
        if (!success) {
          return false;
        }
      }
    }
    return dir.delete();
  }

  public static void main(String[] args) throws FileNotFoundException {
    File graphFile =  new File(args[0]);
    if(args.length > 2 && Boolean.parseBoolean(args[2])){
      deleteDir(graphFile);
    }
    DatabaseManagementService service = new DatabaseManagementServiceBuilder(
        graphFile.toPath()).
        setConfig(GraphDatabaseSettings.keep_logical_logs, "false").
        setConfig(GraphDatabaseSettings.preallocate_logical_logs, false).build();
    GraphDatabaseService graph = service.database("neo4j");
    Transaction tx = graph.beginTx();
    if(args.length > 1){
      File queryFile = new File(args[1]);
      Scanner scanner = new Scanner(queryFile);
      // Read lines from the file until no more are left.
      StringBuilder query = new StringBuilder();
      while (scanner.hasNext()){
        // Read the next name.
        query.append(scanner.next());
        if(query.charAt(query.length()-1) == ';'){
          System.out.println("Executing: "+query+"...");
          tx.execute(query.toString());
          query = new StringBuilder();
        }else{
          query.append(" ");
        }
      }
      if (!query.toString().equals("")){
        System.out.println("Executing: "+query+"...");
      }
    }
    tx.commit();
    tx.close();
    service.shutdown();
  }

}
