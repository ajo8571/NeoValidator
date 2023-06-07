
CREATE
  (a :Student {uid: 0, name: 'Afe' }), (c :Car {id: 'A', nickname: 'Afe\'s Car' }), (a)-[:owns]->(c),
  (t :Person {name: 'Tiffany'})-[:owns]->(:Boat {id: 'B', nickname: 'Tiffs\'s Boat' }),
  (t)-[:owns]->(:Bus {id: 'C', nickname: 'Tiffs\'s Bus' }),
  ( :Person {name: 'Tiffany'})-[:owns {purpose: "personal"}]->(:Boat {id: 'c', nickname: 'Tiff2s\'s Boat' });

MATCH ()-[:owns]-(b) RETURN b