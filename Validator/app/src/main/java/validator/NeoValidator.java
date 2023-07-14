package validator;

import static validator.ConstraintVocabulary.NODE_PROPERTY_SEPARATOR_SYMBOL;
import static validator.ConstraintVocabulary.NODE_RELATIONSHIP_DEFINITION_SYMBOL;
import static validator.DataGraphNodeValidator.validateDataGraphNodes;
import static validator.DataGraphNodeValidator.validateDataNodeProperty;
import static validator.DataGraphRelationshipValidator.validateRelationships;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.cypher.internal.expressions.functions.Relationships;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;


public class NeoValidator {
	public static void showSchemaGraph(GraphDatabaseService schemaGraph){
		Transaction tx = schemaGraph.beginTx();
		for( Node node :tx.getAllNodes()){
			System.out.println("{");
			System.out.println("\tID: "+node.getId());
			System.out.println("\tParent: "+(node.hasProperty ("parent_label")?"parent_label": "root"));
			System.out.println("\tLabels:");
			for (Label label: node.getLabels()){
				System.out.print("\t\t");
				System.out.println(label);
			}
			System.out.println("\tProperties:");
			for(String key : node.getAllProperties().keySet()){
				System.out.print("\t\t");
				System.out.print(key);
				System.out.println(" : "+node.getProperty(key));
			}
			System.out.println("}");
		}
		System.out.println("------------------------Relationships---------------------------");
		for( Relationship r : tx.getAllRelationships()){
			if(!r.isType(RelationshipType.withName("$$childOf"))) {
				String startLabel = (String) r.getProperty("startLabel");
				String endLabel = (String) r.getProperty("endLabel");
				boolean directed = (boolean) r.getProperty("directed");
				if (directed) {
					System.out.println("\n(:" + startLabel + ")-[:" + r.getType() + "]->(:" + endLabel + ")");
				} else {
					System.out.println("\n(:" + startLabel + ")-[:" + r.getType() + "]-(:" + endLabel + ")");
				}
				System.out.println("\tProperties:");
				for (String key : r.getAllProperties().keySet()) {
					System.out.print("\t\t");
					System.out.print(key);
					System.out.println(" : " + r.getProperty(key));
				}
			}
		}
		tx.commit();
		tx.close();
	}

	public static void validateGraph(GraphDatabaseService schemaGraph, GraphDatabaseService dataGraph,
			long rootNodeId){
		validateDataGraphNodes(schemaGraph, dataGraph, rootNodeId);
		validateRelationships(schemaGraph, dataGraph, rootNodeId);
	}

	public static void main(String[] args) throws Exception {
		if (args.length >= 2) {
			final String pathToConstraints = args[0];
			final String dbPath = args[1];
			final String schemaPath;
			if (args.length > 2) {
				schemaPath = args[2];
			} else {
				schemaPath = "/schema_graph/";
			}
			System.out.println(new Date() + " -- Started");
			File schemaGraphFile = new File(schemaPath);
			File dataGraphFile = new File(dbPath);

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

			long rootNodeId = SchemaParser.parseConstraintsAndBuildSchemaGraph(pathToConstraints, schemaGraphFile, true,
					sg);
			showSchemaGraph(sg);
			validateGraph(sg, dg, rootNodeId);
			schemaService.shutdown();
			dataService.shutdown();

		}else{
			throw new Exception("\nNeoValidator Exception: usage: java NeoValidator "
					+ "path_to_json_constraint_object path_to_neo4j_db [path_to_schema_graph]");
		}
	}

}
