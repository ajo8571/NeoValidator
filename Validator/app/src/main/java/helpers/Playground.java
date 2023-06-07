package helpers;

import java.io.File;
import java.util.Date;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import validator.SchemaParser;

public class Playground {

  public static void main(String[] args) throws Exception {
    File schemaGraphFile = new File("C:\\Users\\afeyo\\Desktop\\summer2023\\Capstone_Code\\NeoValidator\\Validator\\app\\src\\main\\resources\\schema_graph");
    File dataGraphFile = new File("C:\\Users\\afeyo\\Desktop\\summer2023\\Capstone_Code\\NeoValidator\\Validator\\app\\src\\main\\resources\\data_graph");

    DatabaseManagementService schemaService = new DatabaseManagementServiceBuilder(
        schemaGraphFile.toPath()).
        setConfig(GraphDatabaseSettings.keep_logical_logs, "false").
        setConfig(GraphDatabaseSettings.preallocate_logical_logs, false).build();
    DatabaseManagementService dataService = new DatabaseManagementServiceBuilder(
        dataGraphFile.toPath()).
        setConfig(GraphDatabaseSettings.keep_logical_logs, "false").
        setConfig(GraphDatabaseSettings.preallocate_logical_logs, false).build();

    GraphDatabaseService sg = schemaService.database("neo4j");
    GraphDatabaseService dg = dataService.database("neo4j");
    Transaction tx = dg.beginTx();
    Result r = tx.execute("MATCH (a:Person) RETURN count(DISTINCT a.name)");

    while(r.hasNext()){
      System.out.println(r.next());
    }

  }

}
