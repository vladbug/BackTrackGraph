package btg;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Stack;

import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultUndirectedGraph;
import org.jgrapht.graph.SimpleDirectedGraph;

import parser_domain.Operation;
import parser_domain.Schema;
import parser_domain.*;
import parser.Parser;

public class BackTrackGraph {

    // I need more information on the node, how do I do that?
    // I still need the information in the operation but I need more than that
    private Graph<Operation,DefaultEdge> btg;

    // Maybe instead of having a node we could have
    // a data structure that associates the operations
    // to the $? I think that would cost a lot tho


    // This map will map the operations URL to the corresponding IDs
    // This will be usefull to help to generate the requires connections
    private Map<String,Operation> operationsURLs; 

    // This map will map a number to the operation, this will be used 

    // This map will be responsible to assign the operation to it's requires list
    // it may be usefull in the longrun for the $ generation in the tests
    private Map<Operation,List<String>> operationsRequires;

    // I will do an optimization in here, instead of storing postT $1 $2 $3 $4 ...
    // we just need $4 and then we just se if the operation is less or equal
    private Map<Operation,Integer> postBag;

    //private Map<Operation,Cardinality> cardinalityInformation;

    private Specification spec;

    public BackTrackGraph(Map<String, Operation> operations) {

        btg = new DefaultDirectedGraph<>(null, null, false);
        operationsURLs = new HashMap<>(100);
        operationsRequires = new HashMap<>(100);
        postBag = new HashMap<>(100);

        // Adding every operation as a vertex
        for (Map.Entry<String, Operation> entry: operations.entrySet()) {
            Operation o = entry.getValue();
            btg.addVertex(o);
            // Will be stored like (POST /tournaments)
            operationsURLs.put("(" + o.getVerb() + " " + o.getUrl() + ")",o);
        }

        

        try {
            String file_loc = "src/main/resources/tournaments-magmact-extended.json";
            spec = Parser.parse(file_loc);
        } catch (Exception e) {
            // TODO: handle exception
        }
        

       
    
        createRequiresConnections();
        inferLinks_v3();
        System.out.println("Printing postBag");
        printPostBag();
        littleTest();

        for(int j = 0; j < 50; j++) {

            System.out.println("This is a new test sequence!");
            List<String> response = generateSequence();
            for(String s : response) {
                System.out.println(s);
            }
            System.out.println("------------------------------");

        }
    
    }


    public void amIcrazy() { // i am not
        Set<Operation> s = btg.vertexSet();

        for(Operation o : s) {
            System.out.println(o.getOperationID());
            List<String> req = o.getRequires();
            for(String r : req) {
                System.out.println(r);
            }
        }
    }

    // This method will create the edges of the requires operation for our graph
    private void createRequiresConnections() { 
        Set<Operation> s = btg.vertexSet();
       

        for(Operation o : s) {
            List<String> requires_list = o.getRequires();
            RequestBodySchema aaa = o.getRequestBody();
            System.out.println("I am this operation " + o.getOperationID() + " "  + "and these are my requestBody parameters: ");
            if(aaa != null) {
                Schema esquima = null;
                //esquima = spec.dereferenceSchema((ReferencedBodySchema) o.getRequestBody());
                esquima = spec.dereferenceSchema(((ReferencedBodySchema) o.getRequestBody()).getName()); 
                
                System.out.println("Name : " +  esquima.getName() + " Type: " + esquima.getType());
                
            }

            System.out.println("------------------------------");
           
            // if(esquima != null) {
            //     System.out.println("Name: " + esquima.getName() + " Type" + esquima.getType());
            // }
            List<String> parsed_list = parseRequires(requires_list);
            operationsRequires.put(o,parsed_list);
            for(String pre : parsed_list) {
                //System.out.println(o.getOperationID());
                //System.out.println("In our list" + pre);
                if(pre.equals("T")) {
                    //System.out.println("T!"); // orange nodes
                }
                else if(pre.contains("request_body")) {
                    // we need to change the way that the pre-conditions with the request_body work
                    // GET /players/request_body(this){playerNIF}
    
                    String[] remove_request_body = pre.split("request_body\\(this\\)");
              
                    // GET /players/{playerNIF}
                     String new_pre = remove_request_body[0] + remove_request_body[1];
                    // System.out.println("This is the new operation without the request body : " + new_pre);
                    // System.out.println(operationsURLs.get(new_pre).getOperationID());
                    //System.out.println(o.getOperationID());
                    //System.out.println(remove_request_body[0]);
                    //Check if it is a self-getter or no! In this scenario it is always the
                    // post of something so we want to know if there is already a post of that
                    if(o.getVerb().equals("POST") && new_pre.contains(o.getUrl())) {
                        //System.out.println(o.getUrl());
                        //System.out.println(new_pre);

                        btg.addEdge(o,operationsURLs.get(new_pre) , new SelfEdge());

                        // Here we will give information to our bag to know the "creators"
                        postBag.put(o,0);

                    }

                    btg.addEdge(o,operationsURLs.get(new_pre) , new RequiresEdge());

                }
                else {
                    // DAR FIX NAQUELES QUE TÊM UM REQUEST_BODY, POIS ELE NAO ESTA NO MAPA
                    // JÁ ESTÁ FIXED
                    // estamos em deletePlayer, (GET /players/{playerNIF}) é o requires
                    // System.out.println(o.getOperationID());
                    // System.out.println(pre);
                    // System.out.println(operationsURLs.get(pre).getOperationID());
                    //btg.addEdge(o, operationsURLs.get(pre),null);
                    // Check if it is a self_getter or no! In this scenario it is always
                    // a delete of something so it check if it exists. So maybe it is
                    // not a "self getter node"
                    btg.addEdge(o,operationsURLs.get(pre) ,new RequiresEdge());

                }
             
            }
        }
        
    }

    private void printPostBag() {
        postBag.forEach((key,value) -> {
            System.out.println(key.getOperationID() + value);
            
        }
        );
    }

   
    
    
    private void inferLinks_v3() {

        // Teve ficar desta forma por causa do erro : ConcurrentModificationException
        // Now that we have the requires edges let's infer the links
        // The links should be a different type of edge
        // For that I created two label classes
        // And we can use the instance of to differenciate them
        
        Set<Operation> set = new HashSet<>(btg.vertexSet());
        Set<Operation> operationsToAdd = new HashSet<>();
        Set<Operation> operationsForTime = new HashSet<>();

        for(Operation o : set) {
            //System.out.println("This is the operation in question " + o.getOperationID());
            Set<DefaultEdge> edge_set = btg.incomingEdgesOf(o);
            System.out.println("I am: " + o.getOperationID());
            for(DefaultEdge e : edge_set) {
                // getPlayer : o
                // a -> getPlayer
                // b -> getPlayer
                // c -> getPlayer
                if(e instanceof SelfEdge) {
                    //btg.addEdge(btg.getEdgeTarget(e), o, new LinkEdge());
                    operationsToAdd.add(btg.getEdgeSource(e));
                }

                if(e instanceof RequiresEdge) {
                    System.out.println("I am adding a link with" + btg.getEdgeSource(e).getOperationID());
                    operationsForTime.add(btg.getEdgeSource(e));
                }
                
            
            }

            for(Operation o_add : operationsToAdd) {
                btg.addEdge(o,o_add, new LinkEdge());
                //btg.addEdge(o,o_add, new TimeEdge());
                //Porque é um grafo com apenas arcos singulares para o mesmo node
                System.out.println(btg.addEdge(o,o_add, new TimeEdge())); // we won't use this
                for(Operation o_connect : operationsForTime) {
                    btg.addEdge(o_connect, o_add, new TimeEdge());
                    System.out.println("This is the edge that I added: " + o_connect.getOperationID() + " ---> " + o_add.getOperationID());
                    
                }
            }

           
            operationsForTime = new HashSet<>();
            operationsToAdd = new HashSet<>();
               
        }
    }

    public void iterateAllEdges() {
        Set<DefaultEdge> set = btg.edgeSet();
        for(DefaultEdge e : set) {
            Operation o_source = btg.getEdgeSource(e);
            Operation o_target = btg.getEdgeTarget(e);
            //System.out.println(btg.getEdge(o_source, o_target)); // esta existe 
            //System.out.println(btg.getEdge(o_target, o_source)); // esta nao existe e ele devolve null, é o correto
            
            if(e instanceof SelfEdge)  {
                System.out.println("This is the edge from: " + o_source.getOperationID() + " ----> " + o_target.getOperationID() + " and I am self edged");

            }

            else if(e instanceof LinkEdge) {
                System.out.println("This is the edge from: " + o_source.getOperationID() + " ----> " + o_target.getOperationID() + " and I am linked edged");

            }

            else if(e instanceof TimeEdge) {
                System.out.println("This is the edge from: " + o_source.getOperationID() + " ----> " + o_target.getOperationID() + " and I am timed edged");
            }
            else {
                System.out.println("This is the edge from: " + o_source.getOperationID() + " ----> " + o_target.getOperationID() + " and I am a requires edge");

            }
        
        }
    }

    // This method will generate one sequence
    private List<String> generateSequence() {
        // This will generate a random number
        // between 0 and the bag.lenght - 1
        // With this we will be able to get
        // a random element from the array
        // Random rnd = new Random();
        Stack<Operation> stack_return = new Stack<>();
        Stack<Operation> stack_control = new Stack<>();
        Set<Operation> set_control = new HashSet<>();
        // int rndNumber = rnd.nextInt(bag.length);
        Operation o = getRandomOperation();
        stack_return.add(o);
        stack_control.add(o);
        set_control.add(o);

        //sSystem.out.println("I am entering the first while");

        while(!stack_control.isEmpty()) {
            Operation to_process = stack_control.pop();
            //System.out.println(to_process.getOperationID());
            // Now given that operation we will backtrack
            Set<DefaultEdge> edge_set = btg.outgoingEdgesOf(to_process);

            // Having the bag $ and the sequence $ , two different things
            // In the sequence it is to differentiate resources
            for(DefaultEdge e : edge_set) {
                Operation op = btg.getEdgeTarget(e);
                //System.out.println("This is my target: " + op.getOperationID());
                if(!(e instanceof SelfEdge) && !set_control.contains(op)) {
                    stack_return.add(op);
                    stack_control.add(op);
                    set_control.add(op);
                }
               
            
            }

        }


        List<String> sequence = new LinkedList<>();
        //System.out.println("I will be stuck here");
        while(!stack_return.isEmpty()) {
            sequence.add(stack_return.pop().getOperationID());
        }
        

        return sequence;


    }

    public void littleTest() {
        // (POST /tournaments)
        // (POST /players)
        // (POST /tournaments/{tournamentId}/enrollments)
        Operation o1 = operationsURLs.get("(POST /tournaments)");
        Operation o2 = operationsURLs.get("(POST /players)");
        Operation o3 = operationsURLs.get("(POST /tournaments/{tournamentId}/enrollments)");

        List<ReturnInfo> list = new LinkedList<>();

        list.add(generateMultipleSequence(o1));
        list.add(generateMultipleSequence(o2));
        list.add(generateMultipleSequence(o3));

        for(ReturnInfo i : list) {
            System.out.println("Printing...");
            Operation to_print = i.getOperation();
            Map<Operation,Integer> m = i.getCardinalities();
            System.out.println("This is the operation: " + to_print.getOperationID());
            System.out.println("And these are my arguments: ");
            m.forEach((key,value) -> {
                System.out.println(key.getOperationID() + " $" + value);
            });
        }

    }

    // Maybe this should just work for the POST operations
    private ReturnInfo generateMultipleSequence(Operation o) {
        // I want to brute test this sequence
        // 1_P : postPlayer
        // 2_P : postPlayer
        // 1_T : postTournament
        // 1_E : postEnrollment
        //Operation o = getRandomOperation();

        // If the operation is in the bag it means
        // that it is a "creator" operation
        // aka POST

        ReturnInfo information = new ReturnInfo(o);
        System.out.println(o.getOperationID());
        if(postBag.containsKey(o)) {
            int cardinality = postBag.get(o);
            cardinality++;
            postBag.put(o, cardinality);
            information.addCardinality(o,cardinality);
        }
        System.out.println("Got here");

        // Now check if we have red links "this means we have arguments to feed our operation"
      
        Set<DefaultEdge> edge_set = btg.outgoingEdgesOf(o);
        System.out.println("Did not get here");
        Map<Operation,Integer> options = new HashMap<>();
      
        for(DefaultEdge e : edge_set) {
            Operation op = btg.getEdgeTarget(e);
            //System.out.println("This is my target: " + op.getOperationID());
            System.out.println("Crashed here");
            if(!(e instanceof SelfEdge) && !(e instanceof RequiresEdge)) {
            
                int c = postBag.get(op);
                c++;
                options.put(op,c);
                
            }
            
        }


        // Now for each argument we want to generate a valid random number
        
        options.forEach((key,value) -> {
       
            // entre 0 e value - 1 original
            // com +1 será entre 1 e value! é o que queremos
            int rnd = new Random().nextInt(value) + 1;
            
            information.addCardinality(key, rnd);
           
            
        }
        );

        return information;


        // This should produce something like : postEnrollment 1 2

    }

    private List<String> generateSequence(Operation o) {
         // This will generate a random number
        // between 0 and the bag.lenght - 1
        // With this we will be able to get
        // a random element from the array
        // Random rnd = new Random();
        Stack<Operation> stack_return = new Stack<>();
        Stack<Operation> stack_control = new Stack<>();
        Set<Operation> set_control = new HashSet<>();
        // int rndNumber = rnd.nextInt(bag.length);
        stack_return.add(o);
        stack_control.add(o);
        set_control.add(o);
        System.out.println(set_control.contains(o));

        System.out.println("I am entering the first while");

        while(!stack_control.isEmpty()) {
            Operation to_process = stack_control.pop();
            System.out.println(to_process.getOperationID());
            // Now given that operation we will backtrack
            Set<DefaultEdge> edge_set = btg.outgoingEdgesOf(to_process);
          

            // Having the bag $ and the sequence $ , two different things
            // In the sequence it is to differentiante resources
            for(DefaultEdge e : edge_set) {
                Operation op = btg.getEdgeTarget(e);
                System.out.println("This is my target: " + op.getOperationID());
                System.out.println(set_control.contains(op));
                if(!(e instanceof SelfEdge) && !set_control.contains(op)) {
                    stack_return.add(op);
                    stack_control.add(op);
                    set_control.add(op);
                    System.out.println("I contatin now this operation: " + op.getOperationID() + " " + set_control.contains(o));

                }
               
            
            }

            for(Operation operacao : stack_control) {
                System.out.println("This is the information in the stack control");
                System.out.println(operacao.getOperationID());
            }

            for(Operation s : set_control) {
                System.out.println("This is the information in the set control");
                System.out.println(s.getOperationID());
            }
    



        }

      

        List<String> sequence = new LinkedList<>();
        System.out.println("I will be stuck here");
        while(!stack_return.isEmpty()) {
            sequence.add(stack_return.pop().getOperationID());
        }
        

        return sequence;
    }

    // It is better to have an array, the time complexity will be better
    // we won't need to iterate every single vertex in the graph
    // We just do a contasnt access to the array
    private Operation getRandomOperation() {

        Set<Operation> operations = btg.vertexSet();
        int size = operations.size();
        // Devolve numero aleatorio entre 0 e size - 1
        int rnd = new Random().nextInt(size);
        int i = 0;
        for(Operation o : operations) {
            if(i == rnd)
                return o;
            i++;
        }

        return null;
    }

    // Is this even generical enough?
    private List<String> parseRequires(List<String> requires) {
        List<String> parsed_requires = new LinkedList<>();
        // [response_code(GET /tournaments/{tournamentId}) == 200]
        for(String s : requires) {
            if(!s.equals("T")) {
                // Every single time that we have a requires
                // we have the response_code wich is always the same
                // for every one, maybe I should use the split in the whole
                // String with the information of "response_code" but
                // for now it will be like this
                String sub_string = s.substring(13); // magic number
                
                // I want to remove the information in the String until ==
                String[] raw = sub_string.split("==");
                parsed_requires.add(raw[0].trim());
            } else {
                parsed_requires.add(s);
            }
        }
       return parsed_requires;
    }


}
