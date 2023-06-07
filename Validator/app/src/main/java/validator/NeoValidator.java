package validator;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.neo4j.configuration.GraphDatabaseSettings;
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
	private static Map<String, HashSet<String>> attributesToCheckForUniqueness = new HashMap<>();
	private static HashSet<String> constraintTypeToSkip = new HashSet<>(){{add("required");}};
	private static HashSet<String> relationshipAttributesToSkip = new HashSet<>(){{add("name");add("startLabel");add("endLabel");add("maxCardinality");add("minCardinality");add("directed");}};
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
		tx.commit();
		tx.close();
	}

	public static void validateGraph(GraphDatabaseService schemaGraph, GraphDatabaseService dataGraph,
			long rootNodeId){
		validateAttributes(schemaGraph, dataGraph, rootNodeId);
		validateRelationships(schemaGraph, dataGraph, rootNodeId);
	}

	private static void validateAttributes(GraphDatabaseService schemaGraph,
			GraphDatabaseService dataGraph, long rootNodeId) {
		Transaction stx = schemaGraph.beginTx();
		ArrayList<Node> stack = new ArrayList<>();
		stack.add(stx.getNodeById(rootNodeId));
		getLabelForNodeValidation(stack, dataGraph);
		checkForNodeAttributeUniqueness(dataGraph);
	}

	private static void checkForNodeAttributeUniqueness(GraphDatabaseService dataGraph) {
		System.out.println("validating uniqueness constraint for all attributes...");
		Transaction tx = dataGraph.beginTx();
		for(String label : attributesToCheckForUniqueness.keySet()){
			for(String attribute : attributesToCheckForUniqueness.get(label)) {
				Result r = tx.execute("MATCH (a:" + label + ") with a."+attribute+" as attr, "
						+ "count(*) as cnt where cnt > 1 RETURN attr, cnt");
				if(r.hasNext()){
					while(r.hasNext()) {
						Map<String, Object> row = r.next();
						System.out.println(
								ValidationFailureReport.uniqueValidationError(attribute, (String) row.get("attr"),
										label, (long) row.get("cnt")));
					}
				}
			}
		}
	}

	private static void getLabelForNodeValidation(ArrayList<Node> stack,
			GraphDatabaseService dataGraph) {
		for(Relationship rel : stack.get(0).getRelationships(Direction.OUTGOING,
				RelationshipType.withName(SchemaParser.CHILD_OF_RELATIONSHIP))){
			Node child = rel.getEndNode();
			stack.add(0,child);
			getLabelForNodeValidation(stack, dataGraph);
		}
		Node current = stack.get(0);
		if(!current.getLabels().iterator().next().name().equals(SchemaParser.ROOT_NODE_LABEL)) {
			System.out.println("\nvalidating Label: " + current.getLabels() + "...");
			validateLabelForNodes(current, stack, dataGraph);
		}
		stack.remove(0);
	}

	private static void getLabelForRelationshipValidation(ArrayList<Node> stack,
			GraphDatabaseService dataGraph) {
		for(Relationship rel : stack.get(0).getRelationships(Direction.OUTGOING,
				RelationshipType.withName(SchemaParser.CHILD_OF_RELATIONSHIP))){
			Node child = rel.getEndNode();
			stack.add(0,child);
			getLabelForRelationshipValidation(stack, dataGraph);
		}
		Node current = stack.get(0);
		validateLabelForRelationships(current, stack, dataGraph);
		stack.remove(0);
	}

	private static void validateLabelForRelationships(Node current, ArrayList<Node> stack, GraphDatabaseService dataGraph) {
		Transaction tx = dataGraph.beginTx();
		String label = current.getLabels().iterator().next().name();
		Result res = tx.execute("MATCH (a:`"+label+"`) RETURN a");
		while(res.hasNext()){
			Map<String, Object> row = res.next();
			validateSchemaStackForRelationships(stack, label, (Node) row.get("a"));
			//System.out.println(((Node) row.getOrDefault("a", null)).getLabels());
		}
		tx.commit();
		tx.close();
	}

	private static void validateSchemaStackForRelationships(ArrayList<Node> schemaNodes, String label, Node dataNode) {
		for(Node schemaNode : schemaNodes){
			for(Relationship rel : schemaNode.getRelationships(Direction.OUTGOING)) {
				if(!rel.getType().name().equals(SchemaParser.CHILD_OF_RELATIONSHIP)) {
					System.out.println(
							"\nvalidating relationship with name: " + rel.getType().name() + " for label "
									+ label);
					validateRelationship(rel, label, dataNode);
				}
			}
		}
	}

	private static void validateRelationship(Relationship rel, String label, Node dataNode) {
		if(dataNode.hasRelationship(Direction.OUTGOING, rel.getType())) {
			if(rel.getProperty("maxCardinality") != null){
				int maxCardinality = (int)rel.getProperty("maxCardinality");
				int minCardinality =  (int)rel.getProperty("minCardinality");
				int numRels = 0;
				for(Relationship r: dataNode.getRelationships(Direction.OUTGOING, rel.getType())){
					numRels+=1;
				}
				if (numRels > maxCardinality){
					System.out.println("VALIDATION FAILURE dataNode with id "+dataNode.getId()+" has "
							+numRels+" Relationships of type "+rel.getType()+" but should have at most"+
							maxCardinality +" based on the maxCardinality constraint on label "+label);
					return;
				}else if (numRels < minCardinality){
					System.out.println("VALIDATION FAILURE dataNode with id "+dataNode.getId()+" has "
							+numRels+" Relationships of type "+rel.getType()+" but should have at least"+
							minCardinality +" based on the minCardinality constraint on label "+label);
					return;
				}
			}

			for(Relationship dataNodeRel : dataNode.getRelationships(rel.getType())) {
				for (String prop : rel.getPropertyKeys()) {
					if(!relationshipAttributesToSkip.contains(prop)) {
						String[] constraint = prop.split("%_");
						String key = constraint[0];
						String constraint_type = constraint[1];
						Object constraint_value = rel.getProperty(prop);
						Object value = dataNodeRel.getProperty(key, null);
						if (!constraintTypeToSkip.contains(constraint_type)) {
							if (value == null && (boolean) rel.getProperty(key + "%_required",
									true)) {
								System.out.println(
										new ValidationFailureReport(label, rel.getType().name(),
												key,
												"required", (String) value, dataNode.getId()));
							} else {
								validateDataNode(constraint_type, constraint_value, label, key, value,
										rel.getType().name(), dataNode);

							}
						}
					}
				}
			}
		}
	}

	private static void validateLabelForNodes(Node current, ArrayList<Node> stack,
			GraphDatabaseService dataGraph) {
		Transaction tx = dataGraph.beginTx();
		String label = current.getLabels().iterator().next().name();
		Result res = tx.execute("MATCH (a:`"+label+"`) RETURN a");
		while(res.hasNext()){
			Map<String, Object> row = res.next();
			validateSchemaStack(stack, label, (Node) row.get("a"));
		}
		tx.commit();
		tx.close();

	}

	private static void validateSchemaStack(ArrayList<Node> schemaNodes,String label, Node dataNode) {
		for(Node schemaNode : schemaNodes){
			validateDataNode(schemaNode, label, dataNode);
		}

	}

	private static void validateDataNode(Node schemaNode, String label, Node dataNode) {
		for(String prop: schemaNode.getPropertyKeys()) {
			String[] constraint = prop.split("%_");
			String key = constraint[0];
			String constraint_type = constraint[1];
			Object constraint_value = schemaNode.getProperty(prop);
			Object value = dataNode.getProperty(key, null);
			if (!constraintTypeToSkip.contains(constraint_type)){
				if (value == null && (boolean) schemaNode.getProperty(key + "%_required",
						true)) {
					System.out.println(
							new ValidationFailureReport(label, schemaNode.getLabels().iterator().next().name(),
									key,
									"required", (String) value, dataNode.getId()));
				} else {
					validateDataNode(constraint_type, constraint_value, label, key, value,
							schemaNode.getLabels().iterator().next().name(), dataNode);

				}
			}
		}
	}

	public static void validateDataNode(String constraint_type, Object constraint_value,
			String dataNodelabel, String key, Object value, String schemaNodeLabel, Node dataNode){
		switch (constraint_type) {
			case "unique":
				if ((boolean) constraint_value){
					if(!attributesToCheckForUniqueness.containsKey(dataNodelabel)) {
						attributesToCheckForUniqueness.put(dataNodelabel, new HashSet<>());
					}
					attributesToCheckForUniqueness.get(dataNodelabel).add(key);
				}
				break;
			case "type":
				switch ((String) constraint_value) {
					case ConstraintVocabulary.NUMBER:
						try {
							int v = Integer.parseInt((String) value);
						} catch (NumberFormatException e) {
							System.out.println(new ValidationFailureReport(dataNodelabel,
									schemaNodeLabel, key, constraint_type,
									(String) value,
									dataNode.getId()));
						}
						break;
					case ConstraintVocabulary.BOOL:
						Boolean v = Boolean.parseBoolean((String) value);
						break;
				}
				break;
		}
	}

	private static void validateRelationships(GraphDatabaseService schemaGraph,
			GraphDatabaseService dataGraph, long rootNodeId) {
		Transaction stx = schemaGraph.beginTx();
		ArrayList<Node> stack = new ArrayList<>();
		stack.add(stx.getNodeById(rootNodeId));
		getLabelForRelationshipValidation(stack, dataGraph);
		//checkForUniqueness(dataGraph);
	}

	public static void main(String[] args) throws Exception {
		if (args.length >= 2) {
			final String pathToConstraints = args[0];
			final String dbPath = args[1];
			final String schemaPath;
			if (args.length > 2) {
				schemaPath = args[2];
			} else {
				schemaPath = "./schema_graph/";
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
			//showSchemaGraph(sg);
			validateGraph(sg, dg, rootNodeId);
			schemaService.shutdown();
			dataService.shutdown();

		}else{
			throw new Exception("\nNeoValidator Exception: usage: java NeoValidator "
					+ "path_to_json_constraint_object path_to_neo4j_db [path_to_schema_graph]");
		}
	}

}
