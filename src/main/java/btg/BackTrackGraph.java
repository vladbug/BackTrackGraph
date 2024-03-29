package btg;


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Stack;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

import extended_parser_domain.*;

public class BackTrackGraph {

    /*
     * The main graph. Nodes are operations, and although there are multiple types of edges
     * the DefaultEdge from the jgrapht library is used to map them all.
     */
    private Graph<Operation, DefaultEdge> btg;

    private Specification spec;

    /**
     * Operations by URL
     */
    private Map<String, Operation> operationsURLs;

    /**
     * This map will be responsible to assign the operation to it's requires list
     */
    private Map<Operation, List<String>> operationsRequires;

    /**
     * Operations by id.
     */
    private Map<String, Operation> operationIDS;

    /**
     * This map will map the VERB of the operation to the operation
     */
    private Map<Operation, String> operationVerbs;

    /**
     * Used to wipe information between different call sequences
     */
    private List<String> postBag;

    /**
     * This is the new version of the postBag
     * It is more advanced and has a lot more information
     * I'm gonna name it : history
     * <p>
     * It will map an operationID into another map
     * with cardinalities and statusses of things
     * This map will only cover ther POST
     * operations as it's keys!
     */
    private Map<String, List<Annotation>> history;

    /**
     * Number of random operations in each sequence
     */
    private int rands;

    /**
     * Number of sequences to be generated
     */
    private int sequences;

    private boolean stop;
    private int threshold;
    private int backtracks;

    /**
     * Creates a Backtrack Graph.
     *
     * @param spec      API extended specification
     * @param rands     number of randomly selected operations in a call sequence
     * @param sequences number of different call sequences to generate
     * @param threshold maximum number of backtracks allowed
     */
    public BackTrackGraph(Specification spec, int rands, int sequences, int threshold) {
        btg = new DefaultDirectedGraph<>(null, null, false);
        this.spec = spec;
        operationsURLs = new HashMap<>(100);
        operationsRequires = new HashMap<>(100);
        operationIDS = new HashMap<>(100);
        operationVerbs = new HashMap<>(100);
        history = new HashMap<>(100);
        postBag = new LinkedList<>();
        //this.nominal = nominal;
        this.rands = rands;
        this.sequences = sequences;
        this.threshold = threshold;
        backtracks = 0;
        Map<String, Operation> operations = spec.getOperations();

        // Adding every operation as a vertex
        for (Map.Entry<String, Operation> entry : operations.entrySet()) {
            Operation o = entry.getValue();
            // Constructing the graph
            btg.addVertex(o);
            // Will be stored like (POST /tournaments)
            operationsURLs.put("(" + o.getVerb() + " " + o.getUrl() + ")", o);
            // Will be store like postTournament Operation : postTournament
            operationIDS.put(o.getOperationID(), o);
            // Will be store Operation and the VERB associated
            operationVerbs.put(o, o.getVerb());
        }

        // Building the graph
        createRequiresConnections();
        inferLinks_v3();
        applyTransitiveFilter();
    }

    /**
     * Prints all generated call sequences
     *
     * @param call_sequences sequences to print
     */
    public void printCallSequences(List<List<Annotation>> call_sequences, boolean nominal) {
        int seq = 1;

        for (List<Annotation> l : call_sequences) {
            System.out.println("\ncall sequence #" + seq);

            if (!nominal)
                if (l.get(l.size() - 1).getStatus() == Status.NEGATIVE)
                    printInformation(l);
                else
                    System.out.println("Did not break the threshold");
            else
                printInformation(l);

            seq++;
        }
    }

    /**
     * Generates call sequences.
     *
     * @param nominal indicates whether the sequences are nominal or faulty
     * @return call sequences
     */
    public List<List<Annotation>> generateCallSequences(boolean nominal) {
        List<List<Annotation>> call_sequences = new LinkedList<>();

        for (int i = 0; i < sequences; i++) {
            int seqnum = i + 1;
            List<Annotation> generated = generateSequence(nominal);
            call_sequences.add(generated);

            // Wipe information for next sequence
            history = new HashMap<>(100);

            for (String s : postBag)
                history.put(s, new LinkedList<>());

            backtracks = 0;
            stop = false;
        }

        return call_sequences;
    }

    /**
     * Shows the structure of the whole graph with it's edges and vertices
     */
    public void iterateAllEdges() {
        Set<DefaultEdge> set = btg.edgeSet();
        for (DefaultEdge e : set) {
            Operation o_source = btg.getEdgeSource(e);
            Operation o_target = btg.getEdgeTarget(e);
            // Directed graph
            // System.out.println(btg.getEdge(o_source, o_target)); // esta existe
            // System.out.println(btg.getEdge(o_target, o_source)); // esta nao existe e ele
            // devolve null, é o correto

            if (e instanceof BlackDashedEdge) {
                System.out.println("This is the edge from: " + o_source.getOperationID() + " ----> "
                        + o_target.getOperationID() + " and I am self edged");

            } else if (e instanceof BlueEdge) {
                System.out.println("This is the edge from: " + o_source.getOperationID() + " ----> "
                        + o_target.getOperationID() + " and I am linked edged");

            } else if (e instanceof RedEdge) {
                System.out.println("This is the edge from: " + o_source.getOperationID() + " ----> "
                        + o_target.getOperationID() + " and I am timed edged");
            } else if (e instanceof GreenEdge) {
                System.out.println("This is the edge from: " + o_source.getOperationID() + " ----> "
                        + o_target.getOperationID() + " and I am yellow edged");

            } else {
                System.out.println("This is the edge from: " + o_source.getOperationID() + " ----> "
                        + o_target.getOperationID() + " and I am a requires edge");

            }

        }
    }

    /**
     * Generates a single call sequence
     *
     * @param nominal indicates whether the sequence is nominal or faulty
     * @return call sequence
     */
    public List<Annotation> generateSequence(boolean nominal) {
        List<Annotation> toReturn = new LinkedList<>();

        for (int i = 0; i < rands; i++) {
            if (stop) break;
            Operation o = getRandomOperation();
            //System.out.println("The random operation is: " + o.getOperationID());
            List<Annotation> resolved = resolve(o, nominal);

            for (Annotation a : resolved)
                toReturn.add(a);
        }

        filterSequence(toReturn);

        return toReturn;
    }

    /**
     * I will perform something called "aldrabar". After the exams I will implement
     * the indexes if necessary because the indexation logic would change a lot of things in
     * the BTG algorithm as a whole.
     * In order to create a "timeline" I detected a pattern. The only thing that causes problems
     * are the POST operations. Therefore a filter in the resulting sequence would solve the problem.
     * I know in terms of computation it's heavier BUT. O(n) + O(n) = O(2N) = O(N) :)
     *
     * @param toFilter
     * @return
     */
    public List<Annotation> filterSequence(List<Annotation> toFilter) {
        for (Annotation a : toFilter) {
            if (a.getOperationVerb().equals("POST")) {
                // This is a post operation
                if (a.getStatus() == Status.UNAVAILABLE) {
                    // Then we want to turn it into available
                    a.setStatus(Status.AVAILABLE);
                }
            }
        }

        return toFilter;
    }


    /**
     * Generates the combinatory
     *
     * @param lists
     * @return - return the combinatory
     */
    public static List<List<Annotation>> generateCombinations(List<List<Annotation>> lists) {
        List<List<Annotation>> combinations = new ArrayList<>();
        int[] indices = new int[lists.size()];

        while (true) {
            List<Annotation> combination = new ArrayList<>();
            for (int i = 0; i < lists.size(); i++) {
                List<Annotation> currentList = lists.get(i);
                int currentIndex = indices[i];
                combination.add(currentList.get(currentIndex));
            }
            combinations.add(combination);

            int listIndex = lists.size() - 1;
            while (listIndex >= 0 && indices[listIndex] == lists.get(listIndex).size() - 1) {
                indices[listIndex] = 0;
                listIndex--;
            }

            if (listIndex < 0) {
                break;
            }

            indices[listIndex]++;
        }

        return combinations;
    }


    /**
     * If we want to get the state of the dataStructure for debug
     * purposes
     */
    private void getStateOfData() {
        List<Annotation> pre_1 = history.get("postPlayer");
        List<Annotation> pre_2 = history.get("postTournament");
        List<Annotation> pre_3 = history.get("postEnrollment");

        for (Annotation i : pre_1) {
            System.out.println(i.getStatus() + i.getOperationId() + i.getTag());
        }
        System.out.println("---------");
        for (Annotation i : pre_2) {
            System.out.println(i.getStatus() + i.getOperationId() + i.getTag());
        }
        System.out.println("---------");
        for (Annotation i : pre_3) {
            System.out.println(i.getStatus() + i.getOperationId() + i.getTag());
        }
        System.out.println("---------");
    }

    /**
     * Print the information relative to the annotation
     *
     * @param info - annotation that we want to extract the information from
     */
    private void printInformation(List<Annotation> info) {
        for (Annotation i : info) {
            System.out.println(i.getOperationId() + " $" + i.getTag() + " " + i.getStatus());

            if (i.hasDependencies()) {
                List<Annotation> args = i.getDependencies();

                for (Annotation args_i : args)
                    System.out.println("  - " + args_i.getOperationId() + " $" + args_i.getTag() + " " + args_i.getStatus());
            }
        }
    }

    /**
     * Given the JSON file we will create the black-edges
     */
    private void createRequiresConnections() {
        Set<Operation> s = btg.vertexSet();

        for (Operation o : s) {
            List<String> requires_list = o.getRequires();

            List<String> parsed_list = parseRequires(requires_list);
            operationsRequires.put(o, parsed_list);
            for (String pre : parsed_list) {
                if (pre.equals("T")) {
                    // These are the nodes can don't have any dependencies
                } else if (pre.contains("request_body")) {
                    // We need to change the way that the pre-conditions with the request_body word
                    // GET /players/request_body(this){playerNIF}
                    String[] remove_request_body = pre.split("request_body\\(this\\)");
                    // (GET /players/{playerNIF})
                    String new_pre = remove_request_body[0] + remove_request_body[1];
                    // Check if it is a self-getter or no! In this scenario it is always the
                    // post of something so we want to know if there is already a post of that
                    if (o.getVerb().equals("POST") && new_pre.contains(o.getUrl())) {

                        btg.addEdge(o, operationsURLs.get(new_pre), new BlackDashedEdge());

                        history.put(o.getOperationID(), new LinkedList<>());
                        postBag.add(o.getOperationID());

                    } else {
                        // This one would fail for postE and postP
                        btg.addEdge(o, operationsURLs.get(new_pre), new BlackEdge());
                    }

                } else {

                    btg.addEdge(o, operationsURLs.get(pre), new BlackEdge());

                }

            }
        }

    }

    /**
     * After the initial building of the graph we must
     * infer all the other dependencies betweeen the nodes
     */
    private void inferLinks_v3() {

        Set<Operation> set = new HashSet<>(btg.vertexSet());
        Set<Operation> operationsToAdd = new HashSet<>();
        Set<Operation> operationsForTime = new HashSet<>();

        for (Operation o : set) {
            Set<DefaultEdge> edge_set = btg.incomingEdgesOf(o);
            for (DefaultEdge e : edge_set) {
                if (e instanceof BlackDashedEdge) {
                    operationsToAdd.add(btg.getEdgeSource(e));
                }

                if (e instanceof BlackEdge) {
                    operationsForTime.add(btg.getEdgeSource(e));
                }

            }

            for (Operation o_add : operationsToAdd) {
                btg.addEdge(o, o_add, new BlueEdge());
                for (Operation o_connect : operationsForTime) {
                    btg.addEdge(o_connect, o_add, new RedEdge());

                    if (o_connect.getVerb().equals("POST")) {
                        btg.addEdge(o_add, o_connect, new GreenEdge());
                    }

                }
            }

            operationsForTime = new HashSet<>();
            operationsToAdd = new HashSet<>();

        }
    }

    /**
     * This is an important step to make. After infering all the edges
     * in the graph we apply a filter to guarantee the life-cycle
     * of each operation
     */
    private void applyTransitiveFilter() {
        Set<Operation> s = btg.vertexSet();

        Map<String, Set<String>> toMaintain = new HashMap<>();

        for (Operation o : s) {
            // This will be performed for EVERY operation in our graph
            // We wan't to get the red links of each one of them
            Set<DefaultEdge> red_edges = new HashSet<>();
            Set<String> red_edges_operations = new HashSet<>();
            Set<DefaultEdge> edges = btg.outgoingEdgesOf(o);

            // Now we are only interesested in the red links
            for (DefaultEdge e : edges) {
                if (e instanceof RedEdge || e instanceof BlueEdge) {
                    red_edges.add(e);
                    red_edges_operations.add(btg.getEdgeTarget(e).getOperationID());
                }
            }

            if (red_edges_operations.size() > 1) {

                // Now let's apply the transitivity rule for it
                List<Set<String>> sets = new LinkedList<>();
                Set<String> beginner_set = new HashSet<>();

                for (String o_red : red_edges_operations) {
                    beginner_set.add(o_red);
                }
                sets.add(beginner_set);

                // Now let's check if we can traverse the edges
                for (String o_red : red_edges_operations) {

                    Set<DefaultEdge> out_destination = btg.outgoingEdgesOf(operationIDS.get(o_red));

                    // Out of these out_destionation edges we only want the red ones
                    // Let's see if it leads to another POSTs
                    Set<String> potencial_set = new HashSet<>();

                    for (DefaultEdge e_dest : out_destination) {
                        if (e_dest instanceof RedEdge) {

                            potencial_set.add(btg.getEdgeTarget(e_dest).getOperationID());
                        }
                    }

                    sets.add(potencial_set);
                }

                Set<String> different_elements = my_method(sets);

                toMaintain.put(o.getOperationID(), different_elements);

                // In different_elements we have the links that we have to maintain!

            }
        }

        for (Operation o : s) {

            Set<DefaultEdge> edges = btg.outgoingEdgesOf(o);
            Set<String> red_edges = new HashSet<>();
            for (DefaultEdge e : edges) {
                if (e instanceof RedEdge) {
                    red_edges.add(btg.getEdgeTarget(e).getOperationID());
                }
            }

            // Now for each of the red links let's remove some
            Set<String> set = toMaintain.get(o.getOperationID());

            if (set != null) {
                red_edges.removeAll(set);
                for (String to_remove : red_edges) {
                    btg.removeEdge(o, operationIDS.get(to_remove));
                }
            }

        }

    }

    /**
     * Will return the edges that we must maintain
     *
     * @param sets - a list of sets
     * @return - a set with the edges that we must maintain
     */
    private Set<String> my_method(List<Set<String>> sets) {
        Set<String> initial_set = sets.get(0);
        Set<String> return_set = initial_set;

        for (int i = 1; i < sets.size(); i++) {
            Set<String> s = sets.get(i);
            if (!s.isEmpty()) {
                return_set.removeAll(s);
            }
        }

        return return_set;

    }

    /**
     * @param o       operation
     * @param nominal indicates whether the sequences are nominal or faulty
     *                In this method we will just decide what to do with
     *                the choices that we have. We will detect which
     *                operation it is and perform logic into it
     *                in order to append to the retrieved list
     */
    private List<Annotation> resolve(Operation o, boolean nominal) {
        List<Annotation> sequence = new LinkedList<>();

        switch (o.getVerb()) {
            case "POST" -> sequence = copeWithPost(o, nominal);
            case "DELETE" -> sequence = copeWithDelete(o, nominal);
            case "GET" -> sequence = copeWithGet(o, nominal);
            case "PUT" -> sequence = copeWithPut(o, nominal);
            // TODO @vladbug exceptions!!!
            default -> System.out.println("I did not match any of the operation verbs available");
        }

        return sequence;
    }

    /**
     * This method will perform the logic for the GET operations
     *
     * @param o       GET operation
     * @param nominal indicates whether the sequences are nominal or faulty
     * @return a list of annotations
     */
    private List<Annotation> copeWithGet(Operation o, boolean nominal) {

        // Here we can have two scenarios , either we back track or not
        // If we do backtrack we will add things to the data structure
        // If we don't backtrack it is just easy

        // These are the getters can can always happen no matter what
        if (btg.outDegreeOf(o) == 0) {
            return List.of(new Annotation(o, Status.AVAILABLE, 404));
        }

        // Let's consider the scenario where we don't need to back-track
        Operation creator = getCreator(o);

        List<Annotation> history_list = history.get(creator.getOperationID());
        Annotation toGet = null;
        int position = 0;

        // Get the first available POST for getter
        for (Annotation info : history_list) {
            if (info.getStatus() == Status.AVAILABLE) {
                toGet = info;
                break;
            }
            position++;
        }

        if (toGet == null) {

            // This mean that there is no available operation to do that
            // or simply the history_list is empty, this means
            // that we will have to backtrack! We must
            // create an enrollment in order to get it!

            // Now this cannot be that simples because in the new graph
            // we will have to explore until we reach "terminal" posts

            Annotation append = new Annotation(o, Status.AVAILABLE, history_list.size() + 1);
            List<Operation> needed = needed(o);
            needed.remove(creator);
            List<Annotation> toReturn;

            if (nominal) {
                toReturn = backTrackPost(creator, needed, append);
            } else {
                // The option given is to create non-optimistic sequences
                if (backtracks < threshold) {
                    toReturn = backTrackPost(creator, needed, append);
                    backtracks++;

                } else {
                    toReturn = new LinkedList<>();
                    append.setStatus(Status.NEGATIVE);
                    stop = true;
                }
            }

            for (Annotation info_update : toReturn) {
                List<Annotation> info_op = history.get(info_update.getOperationId());
                info_op.add(info_update);
            }

            toReturn.add(append);

            return toReturn;

        } else {

            // This means that there is an available one
            // we do not need to backtrack

            // And we already know wich one to use! Update the history
            Annotation info_to_get = history_list.get(position);

            // And we don't want to hash this again tho
            // We will pass a no hash function!
            Annotation to_return = new Annotation(o, Status.AVAILABLE, info_to_get.getTag(), false);
            List<Annotation> toReturn = List.of(to_return);

            return toReturn;
        }
    }

    /**
     * This method will perform the logic for the PUT operations
     *
     * @param o       PUT operation
     * @param nominal indicates whether the sequences are nominal or faulty
     * @return a list of annotations
     */
    private List<Annotation> copeWithPut(Operation o, boolean nominal) {

        // Here we can have two scenarios , either we back track or not
        // If we do backtrack we will add things to the data structure
        // If we don't backtrack it is just easy

        // Let's consider the scenario where we don't need to back-track
        Operation creator = getCreator(o);

        List<Annotation> history_list = history.get(creator.getOperationID());
        Annotation toUpdate = null;
        int position = 0;

        // Get the first available POST for deletion
        for (Annotation info : history_list) {
            if (info.getStatus() == Status.AVAILABLE) {
                toUpdate = info;
                break;
            }
            position++;
        }

        if (toUpdate == null) {

            // This mean that there is no available operation to do that
            // or simply the history_list is empty, this means
            // that we will have to backtrack! We must
            // create an enrollment in order to delete it!

            // Now this cannot be that simples because in the new graph
            // we will have to explore until we reach "terminal" posts
            Annotation append = new Annotation(o, Status.AVAILABLE, history_list.size() + 1);
            List<Operation> needed = needed(o);
            needed.remove(creator);
            List<Annotation> toReturn;

            if (nominal) {
                toReturn = backTrackPost(creator, needed, append);
            } else {
                // The option given is to create non-optimistic sequences
                if (backtracks < threshold) {
                    toReturn = backTrackPost(creator, needed, append);
                    backtracks++;
                } else {
                    toReturn = new LinkedList<>();
                    append.setStatus(Status.NEGATIVE);
                    stop = true;
                }
            }

            for (Annotation info_update : toReturn) {
                List<Annotation> info_op = history.get(info_update.getOperationId());
                info_op.add(info_update);
            }

            toReturn.add(append);

            return toReturn;

        } else {
            // This means that there is an available one
            // we do not need to backtrack
            //System.out.println("I have a tournament to update");
            // And we already know wich one to use! Update the history
            Annotation info_to_get = history_list.get(position);

            Annotation to_return = new Annotation(o, Status.AVAILABLE, info_to_get.getTag(), false);
            List<Annotation> toReturn = List.of(to_return);

            return toReturn;
        }
    }

    /**
     * This method will perform the logic for the DELETE operations
     *
     * @param o       DELETE operation
     * @param nominal indicates whether the sequences are nominal or faulty
     * @return a list of annotations
     */
    private List<Annotation> copeWithDelete(Operation o, boolean nominal) {

        // Let's see if we have uses
        Operation father = getCreator(o);
        Set<DefaultEdge> edge_set = btg.outgoingEdgesOf(father);

        Set<Operation> op_dependecy = new HashSet<>();

        for (DefaultEdge e : edge_set) {
            if (e instanceof GreenEdge) {
                Operation op = btg.getEdgeTarget(e);
                op_dependecy.add(op);
            }
        }

        if (op_dependecy.size() == 0) {

            // This is a POST with no out green edges, it won't trigger a cascade deletion
            // In this specific example it means that this POST is a POST Enrollment

            // Here we just wan't a random enrollment that exists
            // and delete it!

            // Let's get a random enrollment that exist
            Operation creator = getCreator(o);

            List<Annotation> history_list = history.get(creator.getOperationID());
            Annotation toDelete = null;
            int position = 0;

            // Get the first available POST for deletion
            for (Annotation info : history_list) {
                if (info.getStatus() == Status.AVAILABLE) {
                    toDelete = info;
                    break;
                }
                position++;
            }

            if (toDelete == null) {

                // This mean that there is no available operation to do that
                // or simply the history_list is empty, this means
                // that we will have to backtrack! We must
                // create an enrollment in order to delete it!

                // Now this cannot be that simples because in the new graph
                // we will have to explore until we reach "terminal" posts

                List<Operation> needed = needed(o);
                List<Annotation> toReturn;

                if (nominal) {
                    toReturn = backTrackDelete(o, needed);

                } else {
                    if (backtracks < threshold) {
                        toReturn = backTrackDelete(o, needed);
                        backtracks++;
                    } else {
                        List<Annotation> list_root = history.get(getCreator(o).getOperationID());
                        Annotation i_root = new Annotation(o, Status.NEGATIVE, list_root.size() + 1);
                        toReturn = List.of(i_root);
                        stop = true;
                    }

                }

                return toReturn;
            } else {
                // This means that there is an available one
                // we do not need to backtrack

                // And we already know wich one to use! Update the history

                Annotation info_to_delete = history_list.get(position);
                info_to_delete.setStatus(Status.UNAVAILABLE);

                // Here we just want to use a hash that has already been created
                Annotation to_return = new Annotation(o, Status.UNAVAILABLE, info_to_delete.getTag(), false);
                List<Annotation> toReturn = List.of(to_return);

                return toReturn;
            }

        } else {

            // This means that this POST might create a cascade deletion!
            // Here the scenarios will be the same, if we don't have a POST
            // for this deletion then create one and this will never trigger
            // a cascade deletion

            Operation creator = getCreator(o);

            List<Annotation> history_list = history.get(creator.getOperationID());

            Annotation toDelete = null;
            int position = 0;

            for (Annotation info : history_list) {
                if (info.getStatus() == Status.AVAILABLE) {
                    toDelete = info;
                    break;
                }
                position++;
            }

            if (toDelete == null) {

                // This mean that there is no available operation to do that
                // or simply the history_list is empty, this means
                // that we will have to backtrack! We must
                // create an enrollment in order to delete it!

                // When we backtrack we don't need to worry with the cascade deletion

                List<Operation> needed = needed(o);
                List<Annotation> toReturn;

                if (nominal) {
                    toReturn = backTrackDelete(o, needed);
                } else {
                    if (backtracks < threshold) {
                        toReturn = backTrackDelete(o, needed);
                        backtracks++;
                    } else {
                        List<Annotation> list_root = history.get(getCreator(o).getOperationID());
                        Annotation i_root = new Annotation(o, Status.NEGATIVE, list_root.size() + 1);
                        toReturn = List.of(i_root);
                        stop = true;
                    }

                }

                return toReturn;

            } else {

                // This means that there is an available one
                // we do not need to backtrack
                // And we already know wich one to use!
                // The thing here is that it can causa cascade delete!

                Annotation info_to_delete = history_list.get(position);
                // info_to_delete.setStatus(Status.UNAVAILABLE);
                Annotation to_return = new Annotation(o, Status.UNAVAILABLE, info_to_delete.getTag(), false);

                // Now let's check for it's uses!
                Operation uses = spreadCorruption(info_to_delete);

                info_to_delete.setStatus(Status.UNAVAILABLE);

                // Now for the uses let's update the data structure and create the sequence!
                List<Annotation> sequence = cascadeDelete(uses);

                sequence.add(to_return);

                return sequence;
            }

        }

    }

    /**
     * This will retrieve the sequence that we must do the cascade deletion
     * update the data structure and retrieve it
     * //@param toDelete
     *
     * @return
     */
    private List<Annotation> cascadeDelete(Operation o_corrupted) {

        // Let's just get the operation in question!
        List<Annotation> info_corrupted = history.get(o_corrupted.getOperationID());
        List<Annotation> toReturn = new LinkedList<>();

        for (Annotation i : info_corrupted) {
            if (i.getStatus() == Status.CORRUPTED) {
                i.setStatus(Status.UNAVAILABLE);
                Annotation its_delete = extractDeletion(i);
                toReturn.add(its_delete);
            }
        }

        return toReturn;

    }

    /**
     * This will extract a delete operation from the corresponding POST
     *
     * @param i
     * @return
     */
    private Annotation extractDeletion(Annotation i) {

        Set<DefaultEdge> edges = btg.incomingEdgesOf(i.getOperation());
        for (DefaultEdge e : edges) {
            if (btg.getEdgeSource(e).getVerb().equals("DELETE")) {
                return new Annotation(btg.getEdgeSource(e), Status.UNAVAILABLE, i.getTag());
            }
        }
        return null;
    }

    /**
     * Spread the corruption among the operations
     *
     * @param info_of_deletion
     * @return
     */
    private Operation spreadCorruption(Annotation info_of_deletion) {
        // This is receiving postPlayer for instance

        // Let's follow the yellow edges to know where this operation
        // could be used!
        List<Operation> yellow_connection = new LinkedList<>();
        List<Annotation> using_this = new LinkedList<>();
        Operation toReturn = null;

        Set<DefaultEdge> set = btg.outgoingEdgesOf(info_of_deletion.getOperation());

        for (DefaultEdge e : set) {
            if (e instanceof GreenEdge) {
                yellow_connection.add(btg.getEdgeTarget(e));
            }
        }

        // Now let's see if some of them are using it as it's arguments
        for (Operation o : yellow_connection) {
            toReturn = o; // we know that in this example it will only be one -> do more generic after
            // In this example I am assuming that we only lead to one green_connection!
            // If not the using_this should be a List<List<Information>>...
            List<Annotation> child_list = history.get(o.getOperationID());
            // Let's iterate it all and find the ones that might be in use
            for (Annotation i : child_list) {
                if (i.hasDependencies()) {
                    List<Annotation> arguments = i.getDependencies();
                    if (arguments.contains(info_of_deletion) && (i.getStatus() != Status.UNAVAILABLE)) {
                        // This means it will be corrupted so we need to delete it also!
                        i.setStatus(Status.CORRUPTED);
                        using_this.add(i);
                    }
                }
            }

        }

        return toReturn;
    }

    /**
     * Given an operation we want to traverse the graph until
     * we reach the creator. A post that created/has something
     * involved with what we are doing.
     *
     * @param o - the operation of whom we want the "creator"
     * @return - the creator of that operation
     */
    private Operation getCreator(Operation o) {

        Set<DefaultEdge> edges = btg.outgoingEdgesOf(o);
        for (DefaultEdge e : edges) {
            if (e instanceof RedEdge || e instanceof BlueEdge) {
                return btg.getEdgeTarget(e);
            }
        }

        return null; // This will never happen
    }

    /**
     * Gets the needed operations of a certain operation
     *
     * @param o - operation
     * @return - operations the it needs
     */
    private List<Operation> needed(Operation o) {

        Set<DefaultEdge> edge_set = btg.outgoingEdgesOf(o);

        List<Operation> needed = new LinkedList<>();

        Stack<Operation> control = new Stack<>();

        for (DefaultEdge e : edge_set) {
            if (e instanceof RedEdge || e instanceof BlueEdge) {
                Operation op = btg.getEdgeTarget(e);
                needed.add(op);
                control.add(op);
            }
        }

        while (!control.isEmpty()) {
            Operation next = control.pop();
            edge_set = btg.outgoingEdgesOf(next);
            for (DefaultEdge e : edge_set) {
                if (e instanceof RedEdge) {
                    Operation op = btg.getEdgeTarget(e);
                    needed.add(op);
                    control.add(op);
                }
            }
        }

        return needed;
    }

    /**
     * Does the backtrack logic for the delete operation.
     *
     * @param root   - starting node of the the backtrack
     * @param needed - operations that it needs
     * @return - return the annotations that it backtracked
     */
    private List<Annotation> backTrackDelete(Operation root, List<Operation> needed) {
        List<Annotation> toReturn = new LinkedList<>();
        Stack<Annotation> stack = new Stack<>();

        List<Annotation> list_root = history.get(getCreator(root).getOperationID());
        Annotation i_root = new Annotation(root, Status.UNAVAILABLE, list_root.size() + 1);
        stack.push(i_root);
        needed.removeAll(List.of(getCreator(root)));

        List<Annotation> backTrackedInfo = backTrackPost(getCreator(root), needed, i_root);

        // This is very important, update the data structure with the backtracked info!
        for (Annotation info_update : backTrackedInfo) {
            List<Annotation> info_op = history.get(info_update.getOperationId());
            info_op.add(info_update);
        }

        Collections.reverse(backTrackedInfo); // we must do this cause if we don't we lose the effect of the stack

        for (Annotation i : backTrackedInfo) {
            if (i.hasDependencies()) {
                List<Annotation> args = i.getDependencies();
                for (Annotation i_args : args) {
                    i_args.setStatus(Status.UNAVAILABLE);
                }
            }
            i.setStatus(Status.UNAVAILABLE);
            stack.push(i);

        }
        while (!stack.isEmpty()) {
            toReturn.add(stack.pop());
        }

        return toReturn;
    }

    /**
     * This method will perform the logic for the POST operations
     *
     * @param o       simple POST operation
     * @param nominal indicates whether the sequences are nominal or faulty
     * @return a list of annotations
     */
    private List<Annotation> copeWithPost(Operation o, boolean nominal) {

        Set<DefaultEdge> edge_set = btg.outgoingEdgesOf(o);
        boolean simple = true;
        for (DefaultEdge e : edge_set) {
            if (e instanceof RedEdge) {
                simple = false;
                break;
            }
        }

        // simple POST
        if (simple) {
            return copeWithSimplesPost(o);
        }
        // compound POST
        else {
            return copeWithCompoundPost(o, nominal);
        }
    }

    /**
     * Deals with the simple posts
     *
     * @param o - operation
     * @return - annotations for the sequence
     */
    private List<Annotation> copeWithSimplesPost(Operation o) {

        List<Annotation> list = history.get(o.getOperationID());

        Status status = Status.AVAILABLE;

        if (list.isEmpty()) {
            // DOESN'T DEPEND ON ANYTHING
            // First entry
            int tag = 1;
            Annotation i = new Annotation(o, status, tag);
            list.add(i);
            List<Annotation> toReturn = List.of(i);
            return toReturn;
        } else {
            // DOESN'T DEPEND ON ANYTHING
            int index = list.size();
            Annotation last_info = list.get(index - 1);
            Annotation i = new Annotation(o, status, last_info.getTag() + 1);
            list.add(i);
            List<Annotation> toReturn = List.of(i);
            return toReturn;
        }
    }

    /**
     * Deals with the compound posts
     *
     * @param o       compound POST operation
     * @param nominal indicates whether the sequences are nominal or faulty
     * @return annotations for the sequence
     */
    private List<Annotation> copeWithCompoundPost(Operation o, boolean nominal) {
        // DOESN'T DEPENDE ON ANYTHING!
        List<Annotation> list = history.get(o.getOperationID());

        if (list.isEmpty()) {
            // First entry
            int tag = 1;
            Annotation i = new Annotation(o, Status.AVAILABLE, tag);
            // Let's get it's arguments, and in here we might have 2 scenarios
            // where we have the arguments and where we don't and need to backtrack
            List<Annotation> result = getValidArguments(o);

            if (result != null) {
                // Re-using existent ones
                for (Annotation info : result) {
                    i.addDependency(info);
                }

                list.add(i);
                List<Annotation> toReturn = List.of(i);
                return toReturn;
            } else {
                // We must backtrack

                List<Operation> needed = needed(o);
                List<Annotation> toReturn;
                if (nominal) {

                    List<Annotation> backtracked = backTrackPost(o, needed, null);
                    for (Annotation info_update : backtracked) {
                        List<Annotation> info_op = history.get(info_update.getOperationId());
                        info_op.add(info_update);
                    }
                    toReturn = backtracked;
                } else {
                    if (backtracks < threshold) {
                        List<Annotation> backtracked = backTrackPost(o, needed, null);
                        for (Annotation info_update : backtracked) {
                            List<Annotation> info_op = history.get(info_update.getOperationId());
                            info_op.add(info_update);
                        }
                        toReturn = backtracked;
                        backtracks++;
                    } else {
                        Annotation new_info = new Annotation(o, Status.NEGATIVE,
                                history.get(o.getOperationID()).size() + 1);
                        toReturn = List.of(new_info);
                        stop = true;
                    }
                }

                return toReturn;
            }

        } else {

            int index = list.size();
            Annotation last_info = list.get(index - 1);
            Annotation i = new Annotation(o, Status.AVAILABLE, last_info.getTag() + 1);

            List<Annotation> result = getValidArguments(o);

            if (result != null) {

                // Re-using existent ones
                for (Annotation info : result) {
                    i.addDependency(info);
                }

                list.add(i);
                List<Annotation> toReturn = List.of(i);
                return toReturn;
            } else {
                // We must backtrack
                List<Operation> needed = needed(o);
                List<Annotation> toReturn;
                if (nominal) {
                    List<Annotation> backtracked = backTrackPost(o, needed, null);
                    for (Annotation info_update : backtracked) {
                        List<Annotation> info_op = history.get(info_update.getOperationId());
                        info_op.add(info_update);
                    }
                    toReturn = backtracked;

                } else {
                    if (backtracks < threshold) {
                        List<Annotation> backtracked = backTrackPost(o, needed, null);
                        for (Annotation info_update : backtracked) {
                            List<Annotation> info_op = history.get(info_update.getOperationId());
                            info_op.add(info_update);
                        }
                        toReturn = backtracked;
                        backtracks++;
                    } else {
                        Annotation new_info = new Annotation(o, Status.NEGATIVE,
                                history.get(o.getOperationID()).size() + 1);
                        toReturn = List.of(new_info);
                        stop = true;
                    }
                }
                return toReturn;
            }
        }

    }

    /**
     * Gets the arguments that can be used for a POST operation
     *
     * @param o - operation
     * @return - the valid arguments
     */
    private List<Annotation> getValidArguments(Operation o) {
        // We are getting the operations needed for this execution to proceed

        List<Operation> needed = needed(o);

        // Now by having them let's run our method that creates the combinatory
        List<Annotation> possibility = getPossibility(needed, o);

        return possibility;
    }

    /**
     * Gets a valid possibility for the arguments needed for a certain operation
     *
     * @param needed - operations needed
     * @param o      - operation in question
     * @return - the operations of the possibility
     */
    private List<Annotation> getPossibility(List<Operation> needed, Operation o) {

        List<List<Annotation>> generated_possibilities = new LinkedList<>();
        for (Operation n : needed) {
            List<Annotation> info = history.get(n.getOperationID());
            List<Annotation> to_generate = new LinkedList<>();
            for (Annotation i : info) {
                if (i.getStatus() == Status.AVAILABLE) {
                    to_generate.add(i);
                }
            }

            // We can check if ONE OF them has absolutely nothing
            // if one of them has nothing we need to re-create them
            // This was the bug fix for when we had nothing in our history
            if (to_generate.size() == 0) { // This was the strange bug fix!
                return null;
            }

            generated_possibilities.add(to_generate);
        }

        List<List<Annotation>> result = generateCombinations(generated_possibilities);

        // Since we are selecting all of them now we need to extra check if they all are
        // available

        // Given the combinatory let's check if there isn't already an enrollment with
        // that combination
        List<Annotation> my_list = history.get(o.getOperationID());

        for (List<Annotation> list : result) {
            int counter = 0;
            for (Annotation i : my_list) {
                if (!i.hasTheSameDependencies(list)
                        || (i.getStatus() == Status.UNAVAILABLE && i.hasTheSameDependencies(list))) {
                    counter++;
                }
            }

            if (counter == my_list.size()) {
                // This means the combination is applicable, is a valid one to use
                return list;
            }
        }

        return null;
    }

    /**
     * Applies the logic of the backtracking algorithm with the POST operation
     *
     * @param root   - initial node for the backtrack
     * @param needed - operations that it neeeds
     * @return - annotations backtracked
     */
    private List<Annotation> backTrackPost(Operation root, List<Operation> needed, Annotation caller) {
        // DOENS'T DEPEND ON ANYTHING! -> false
        // We already know the operations that we have to back-track
        // Let's change this method to be able to be used in
        // simple posts and compound posts

        Set<DefaultEdge> edge_set = btg.outgoingEdgesOf(root);
        boolean simple = true;

        for (DefaultEdge e : edge_set)
            if (e instanceof RedEdge) {
                simple = false;
                break;
            }

        List<Annotation> toReturn = new LinkedList<>();

        // I had to do this in order for the delete to work
        if (simple) {
            Annotation new_info = new Annotation(root, Status.AVAILABLE, caller.getTag(), false);
            toReturn = List.of(new_info);
        }

        // else if(caller.getOperationVerb().equals("GET")) {
        //     Annotation new_info = new Annotation(root, Status.AVAILABLE, caller.getTag(), false);
        //     toReturn = List.of(new_info);
        // }

        else if ((caller != null && caller.getOperationVerb().equals("GET"))
                || (caller != null && caller.getOperationVerb().equals("DELETE"))
                || (caller != null && caller.getOperationVerb().equals("PUT"))) {
            Stack<Annotation> stack = new Stack<>();
            List<Annotation> list_root = history.get(root.getOperationID());
            Annotation i_root = new Annotation(root, Status.AVAILABLE, caller.getTag(), false);

            for (Operation o : needed) {
                Annotation i_father = new Annotation(o, Status.AVAILABLE, history.get(o.getOperationID()).size() + 1);
                i_root.addDependency(i_father);
            }

            stack.push(i_root);

            // Now let's put into the stack the operations that it needs
            for (Operation o : needed) {
                List<Annotation> list_op = history.get(o.getOperationID());
                Annotation i = new Annotation(o, Status.AVAILABLE, list_op.size() + 1);
                stack.push(i);
            }

            while (!stack.isEmpty())
                toReturn.add(stack.pop());
        } else {
            Stack<Annotation> stack = new Stack<>();
            List<Annotation> list_root = history.get(root.getOperationID());
            Annotation i_root = new Annotation(root, Status.AVAILABLE, list_root.size() + 1);

            for (Operation o : needed) {
                Annotation i_father = new Annotation(o, Status.AVAILABLE, history.get(o.getOperationID()).size() + 1);
                i_root.addDependency(i_father);
            }

            stack.push(i_root);

            // Now let's put into the stack the operations that it needs
            for (Operation o : needed) {
                List<Annotation> list_op = history.get(o.getOperationID());
                Annotation i = new Annotation(o, Status.AVAILABLE, list_op.size() + 1);
                stack.push(i);
            }

            while (!stack.isEmpty())
                toReturn.add(stack.pop());
        }

        return new ArrayList<>(toReturn);
    }

    /**
     * Gets a random operation for our graph
     *
     * @return - the random operation
     */
    private Operation getRandomOperation() {

        Set<Operation> operations = btg.vertexSet();
        int size = operations.size();
        // Devolve numero aleatorio entre 0 e size - 1
        int rnd = new Random().nextInt(size);
        int i = 0;

        for (Operation o : operations) {
            if (i == rnd)
                return o;
            i++;
        }

        return null;
    }

    // Is this even generic enough?

    /**
     * Parses the requires string
     *
     * @param requires - requires string
     * @return - parsed requires
     */
    private List<String> parseRequires(List<String> requires) {
        List<String> parsed_requires = new LinkedList<>();
        // [response_code(GET /tournaments/{tournamentId}) == 200]
        for (String s : requires) {
            if (!s.equals("T")) {
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

    // -- ADDING SOME RANDOMNESS 


    public List<List<Annotation>> generateRandomSequences() {

        List<List<Annotation>> toReturn = new LinkedList<>();

        for (int i = 1; i <= this.sequences; i++) {
            List<Annotation> sequence = generateRandomSequence();
            toReturn.add(sequence);
        }

        return toReturn;
    }

    // Generates only one random sequence
    public List<Annotation> generateRandomSequence() {

        List<Annotation> toReturn = new LinkedList<>();

        for (int i = 1; i <= this.rands; i++) {
            Operation o = getRandomOperation();
            toReturn.add(generateAnnotation(o));
        }

        return toReturn;

    }

    // Generate a pseudo-random annotation for a given operation
    private Annotation generateAnnotation(Operation o) {

        int tag = new Random().nextInt(this.rands) + 1;

        // Generate a random number between 0 and 1
        double rnd = new Random().nextDouble();

        Status status = Status.UNAVAILABLE;

        if (rnd < 0.5) {
            status = Status.AVAILABLE;
        }

        Annotation a = new Annotation(o, status, tag);

        return a;
    }

}
