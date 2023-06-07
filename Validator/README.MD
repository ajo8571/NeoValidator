# NeoValidator 
## File Description:
-  [NeoValidator](https://github.com/ajo8571/NeoValidator/blob/main/Validator/app/src/main/java/validator/NeoValidator.java): Main Program to be run for validating schema 
- [SchemaParser](https://github.com/ajo8571/NeoValidator/blob/main/Validator/app/src/main/java/validator/SchemaParser.java): This file contains code for parsing the schema 
- [ConstraintVocabulary](https://github.com/ajo8571/NeoValidator/blob/main/Validator/app/src/main/java/validator/ConstraintVocabulary.java): This file has constants relevant to parsing the schema defined
- [ValidationFailureReport](https://github.com/ajo8571/NeoValidator/blob/main/Validator/app/src/main/java/validator/ValidationFailureReport.java): This is a class that is used to hold information about validation failures 
- [constraint directory](https://github.com/ajo8571/NeoValidator/tree/main/Validator/app/src/main/resources/constraints): A directory containing examples of both valid and invalid schema definitions 
- [cypher_queries directory](https://github.com/ajo8571/NeoValidator/tree/main/Validator/app/src/main/resources/cypher_queries): A directory containing cypher queries I want to save/play with
- [data_graph directory](https://github.com/ajo8571/NeoValidator/tree/main/Validator/app/src/main/resources/data_graph): This directory holds the test data graph 
- [schema_graph](https://github.com/ajo8571/NeoValidator/tree/main/Validator/app/src/main/resources/data_graph): This directory holds the test schema graph
- [GraphBuilder](https://github.com/ajo8571/NeoValidator/blob/main/Validator/app/src/main/java/helpers/GraphBuilder.java): This directory has code which can be used to create a data graph.

## Instructions to run:
1. run `java GraphBuilder directory_to_put_data_graph path_to_file_containing_query_to_create_graph true` Execute this line with q1.cypher as the cypher query to use the default test data_graph
2. run `java NeoValidator path_to_json_constraint_object path_to_neo4j_db [path_to_schema_graph]` Execute this with one of the already defined json constraint files or feel free to make your own
