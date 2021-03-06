import javafx.util.Pair;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * this class get query (string with spaces), parse it to final terms dictionary @link {@link Parser#_termList}.
 * for this dictionary<term,tf> this class return list of 50 most relevant documents using {@link Ranker}
 * the main function in this class is {@link #Search(String, String, boolean)}
 */
public class Searcher {

    private Ranker _ranker = new Ranker();
    private int _numOfIndexedDocs;
    private double _avgldl = 0; //average document length
    private HashSet<String> _chosenCities; //if (_chosenCities.size()==0|| _chosenCities.contains(city)) add it. else- not
    private Map<String, Vector<Integer>> dictionary;
    private boolean toStem;


    /**
     * hashmap of
     * docline in {@link Indexer#documents} to Vector of Pairs of term-tf.
     */
    private HashMap<Integer, Vector<Pair<String, Integer>>> _doc_termPlusTfs;
    private static HashMap<Integer, Integer> _doc_size = new HashMap<>();
    private HashMap<Integer, Vector<Pair<String,Integer>>> _doc_Entities = new HashMap<>();
    private static HashMap<String, Integer> _term_docsCounter = new HashMap<>();
    private String _path = "";
    private boolean isSemantic;
    private String _toSave="";


    /**
     *
     * @param avgldl
     * @param numOfIndexedDocs
     * @param path
     * @param chosenCities
     * @param toStem
     * @param pathToSave
     * constructor
     */
    public Searcher(double avgldl, int numOfIndexedDocs, String path, HashSet<String> chosenCities,boolean toStem,String pathToSave) {
        _numOfIndexedDocs = numOfIndexedDocs;
        _avgldl = avgldl;//_indexer.getAvgldl();no!
        _chosenCities = chosenCities;
        _path=path;
        this.toStem=toStem;
        _toSave=pathToSave;
    }

    /**Auxiliary functions for Search**/

    /**
     * build {@link #_doc_termPlusTfs} using {@link #addDocsTo_doc_termPlusTfs(List<String>)}
     *
     * @param query  - query to search
     * @param toStem if stem is needed
     */
    private void build_doc_termPlusTfs(String query, boolean toStem) {
        if(isSemantic)
            query= treatSemantic(query);
        StopWords.setStopwords("");
        Parser queryParser = new Parser();
        queryParser.Parse(query, toStem, "");
        HashMap<String, Integer> termList = queryParser.getTerms();
        Set<String> terms = termList.keySet();
        Vector<List<String>> termsSorted=sortTerms(terms);
        for(int i=0;i<termsSorted.size();i++) {
            addDocsTo_doc_termPlusTfs(termsSorted.get(i));
        }
        dictionary.clear();//todo
    }

    private Vector<List<String>> sortTerms(Set<String> terms) {
        List myList = new ArrayList(terms);
        Collections.sort(myList);
        Vector<List<String>> sorted=new Vector<>();
        Iterator<String> iterator=myList.iterator();
        char c='^';
        List<String> list=null;
        while (iterator.hasNext()){
            String term=iterator.next();
            char first=term.charAt(0);
            if(Character.isLetter(first)){
                first=Character.toLowerCase(first);
            }
            else{
                first='0';
            }
            if(first!=c){//new
                if(list!=null){
                    sorted.add(list);
                }
                list=new ArrayList<>();
                list.add(term);
            }
            else{//old
                list.add(term);
            }
        }
        return sorted;

    }

    /**
     * return the query with semantic similiar words appended.
     * @param query
     * @return
     */
    private String treatSemantic(String query) {
        if(isSemantic){
            String queryWithPluses=  queryWithPluses(query);
            try{
                String urlString = "https://api.datamuse.com/words?ml=" + queryWithPluses;
                URL url = new URL(urlString);
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                InputStreamReader iSR =new InputStreamReader((con.getInputStream()));
                BufferedReader bufferedReader =new BufferedReader(iSR);
                String result = bufferedReader.readLine();
                JSONArray words = new JSONArray(result);
                int minLength = 5;
                StringBuffer similiarSemanticWords= new StringBuffer();
                for (int i = 0; i<words.length() && i < minLength  ; i++) {
                    JSONObject wordRecord = (JSONObject) words.get(i);
                    String wordString = (String) wordRecord.get("word");
                    similiarSemanticWords.append(" "+wordString);
                    }
                bufferedReader.close();
                iSR.close();
                return query + similiarSemanticWords.toString();
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else return query;
        return query;
    }

    /**
     *
     * @param query
     * @return query with "+" between
     */
    private String queryWithPluses(String query) {

        String [] words=query.trim().split("\\s+");
        String queryWithPluses="";
        for (int i = 0; i < words.length ; i++) {
            if(i==0)
                queryWithPluses=words[i];
            else{
                queryWithPluses+= ("+" + words[i]);
            }
        }
        return queryWithPluses;
    }


    /**
     * Auxiliary function for {@link #build_doc_termPlusTfs(String, boolean)}
     * add docs that the term appear in them to our {@link #_doc_termPlusTfs} using {@link #readTermDocs(List<String>)}
     *
     * @param terms - the term that adding the docs that include it.
     *             working in concurrency
     */
    private void addDocsTo_doc_termPlusTfs(List<String> terms) {
        Map<Integer, Integer> doc_tf = readTermDocs(terms);

        System.out.println("done add to doc tfs");

    }

    /**
     * Auxiliary function for {@link #build_doc_termPlusTfs(String, boolean)}
     * using {@link #_chosenCities} and not insert doc that their city is one of the cities given from the user
     * if meet doc in the first time, add his entities and his size to their maps( {@link #_doc_size} {@link #_doc_Entities}).
     *
     * @param terms - the docs that include  the term
     * @return doc_tf - list of "doc-tf"s for the above term
     */
    private Map<Integer, Integer> readTermDocs(List<String> terms) {
        Map<Integer, Integer> doc_tf = new HashMap<>();
        String term=terms.get(0);
        char letter = term.charAt(0);
        if (Character.isUpperCase(letter))
            letter = Character.toLowerCase(letter);
        else if (Character.isDigit(letter) || letter == '-')
            letter = '0';
        String fullPath = _path + '/'+Character.toLowerCase(letter) + toStem + "Done.txt";
        if (!dictionary.containsKey(term.toLowerCase())&&!dictionary.containsKey(term.toUpperCase())) {//the term is not in the dictionary
            return doc_tf;
        }
        try {
            //RandomAccessFile raf = new RandomAccessFile(new File(fullPath), "r");
            BufferedReader reader=new BufferedReader(new FileReader(new File(fullPath)));
            int numOfDocs;

            /**
            for (int i = 0; i <= numOfDocs; i++) {
                raf.seek((lineNum + i) * 8);
                byte[] docLine_bytes = new byte[4];
                raf.read(docLine_bytes);
                byte[] tf_bytes = new byte[4];
                raf.seek((lineNum + i) * 8+4);
                raf.read(tf_bytes);
                int docLine = byteToInt(docLine_bytes);
                int tf = byteToInt(tf_bytes);
                if(!doc_tf.keySet().contains(docLine))
                    doc_tf.put(docLine,tf);

                }
            raf.close();
             **/
            int j=0;
            boolean flag=false;
            String termLine;
            String currentTerm=terms.get(j);
            while(!flag){
                termLine=reader.readLine();
                String[] details=termLine.split("~");
                if(details[0].equalsIgnoreCase(currentTerm)) {//need to get numOfDocs for term-term_docsCounter
                    //new term, add details for new one!
                    if (dictionary.containsKey(currentTerm)) {
                        //lineNum = dictionary.get(term).elementAt(2) - 1;//the pointer;
                        numOfDocs = dictionary.get(currentTerm).elementAt(0);//num of docs
                    } else {
                        if (currentTerm.equals(currentTerm.toUpperCase())) {
                            //  lineNum = dictionary.get(term.toLowerCase()).elementAt(2) - 1;//the pointer;
                            numOfDocs = dictionary.get(currentTerm.toLowerCase()).elementAt(0);//num of docs
                        } else {
                            //lineNum = dictionary.get(term.toUpperCase()).elementAt(2) - 1;//the pointer;
                            numOfDocs = dictionary.get(currentTerm.toUpperCase()).elementAt(0);//num of docs
                        }
                        //-1 cause we checked
                    }
                    _term_docsCounter.put(currentTerm, numOfDocs);
                    boolean stopFor=false;
                    //read all lines for new term
                    int i=0;
                    while( !stopFor) {
                        if (!details[0].equalsIgnoreCase(currentTerm)) {//not the same - we got to a new term
                            //System.out.println("somethings is wrong: " + currentTerm);
                            stopFor=true;
                            writeDoc_tfTo_doc_termPlusTfs(doc_tf,currentTerm);//write tto the big map(done with this term!)
                            doc_tf=new HashMap<>();
                            if (j < terms.size()-1) {//there are more terms
                                j++;
                                currentTerm = terms.get(j);
                            } else {//no more terms!
                                flag = true;
                            }
                        } else {//the term is ok

                            //write to doc_tf for the current term ,the current doc.
                            int docLine = (Integer.parseInt(details[1]));
                            int tf = (Integer.parseInt(details[2]));
                            if (!doc_tf.keySet().contains(docLine))
                                doc_tf.put(docLine, tf);

                            termLine = reader.readLine();
                            details = termLine.split("~");
                            }
                            i++;

                        }
                    }
                }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return doc_tf;
    }

    private void writeDoc_tfTo_doc_termPlusTfs(Map<Integer, Integer> doc_tf,String currentTerm) {
        Iterator<Integer> docs = doc_tf.keySet().iterator();
        while (docs.hasNext()) {
            Integer docNum = docs.next();
            Integer termTfInDoc = doc_tf.get(docNum);
            if (_doc_termPlusTfs.containsKey(docNum)) {
                _doc_termPlusTfs.get(docNum).add(new Pair<String, Integer>(currentTerm, termTfInDoc));
            } else {
                _doc_termPlusTfs.put(docNum, new Vector<Pair<String, Integer>>());
                _doc_termPlusTfs.get(docNum).add(new Pair<String, Integer>(currentTerm, termTfInDoc));
            }
        }
    }

    /**
     * @param bytes
     * @return take byte[4] and turn into int- will use next part of project
     */
    private int byteToInt(byte[] bytes) {
        String s2="";
        for (int i=0; i<4; i++){
            String x = String.format("%8s", Integer.toBinaryString(bytes[i] & 0xFF)).replaceAll("\\s","");
            for (int j=8-x.length(); j>0; j--){
                s2 =s2 + "0";
            }
            s2 = s2  +  x;
        }
        s2 = s2.replaceAll("\\s","");
        int foo = Integer.parseInt(s2, 2);
        return foo;
    }


    /**
     * using for {@link #Search(String, String, boolean)}
     * copy from {@link Indexer#loadDictionaryToMemory()}
     * this function load to dictionary ( if not loaded yet) the information from dictionary file.
     * return true if successful, false otherwise
     */
    private boolean loadDictionaryToMemory(boolean toStem) {
        if (dictionary == null||dictionary.size()==0) {
            try {
                dictionary = new HashMap<>();
                BufferedReader bufferedReader = new BufferedReader(new FileReader(_path + "/" + toStem + "Dictionary.txt"));
                String line = bufferedReader.readLine();
                while(line!=null&&!line.equals("")) {
                    String[] pair = line.split("--->");
                    if (pair.length == 2) {
                        String[] values = pair[1].split("&");
                        int df = Integer.parseInt(values[0].substring(1, values[0].length()));
                        int tf = Integer.parseInt(values[1]);
                        int ptr = Integer.parseInt(values[2].substring(0, values[2].length() - 1));
                        Vector<Integer> vector = new Vector<>();
                        vector.add(df);
                        vector.add(tf);
                        vector.add(ptr);
                        dictionary.put(pair[0], vector);
                    }
                    line=bufferedReader.readLine();
                }
                bufferedReader.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return true;
    }


    /**
     *
     * @param ans
     * put all entities from Entities+toStem+.txt to _doc_Entities
     */
    public void getEntities(Collection<Integer> ans) {
        try {
            RandomAccessFile raf=new RandomAccessFile(_path+"/Entities"+toStem+".txt","r");
            for (Integer docNum : ans) {
                byte[]line=new byte[120];
                raf.seek(docNum*120);
                raf.read(line);//120 bytes each term!
                Vector<Pair<String,Integer>> Entities = findEntities(line);
                if(Entities.size()==1&&(Entities.get(0).getKey().equals("X"))){
                    if (!_doc_Entities.containsKey(docNum))
                        _doc_Entities.put(docNum, new Vector<>());//no entities for doc
                }
                else {
                    if (!_doc_Entities.containsKey(docNum))
                        _doc_Entities.put(docNum, Entities);
                }
            }
            raf.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     *
     * @param line-byte[100] - 5 entities in it
     * @return vector of the 5 entities in the line
     */
    private Vector<Pair<String,Integer>> findEntities(byte[] line) {
        byte [] e1=new byte[20];
        byte [] e2=new byte[20];
        byte [] e3=new byte[20];
        byte [] e4=new byte[20];
        byte [] e5=new byte[20];
        byte [] f1=new byte[4];
        byte [] f2=new byte[4];
        byte [] f3=new byte[4];
        byte [] f4=new byte[4];
        byte [] f5=new byte[4];
        for(int i=0;i<20;i++){
            //entity1
            e1[i]=line[i];
            //entity2
            e2[i]=line[i+24];
            //entity3
            e3[i]=line[i+48];
            //entity4
            e4[i]=line[i+72];
            //entity5
            e5[i]=line[i+96];
        }
        for(int i=0;i<4;i++){
            f1[i]=line[i+20];
            f2[i]=line[i+44];
            f3[i]=line[i+68];
            f4[i]=line[i+92];
            f5[i]=line[i+116];

        }
        String entity1=new String(e1);
        String entity2=new String(e2);
        String entity3=new String(e3);
        String entity4=new String(e4);
        String entity5=new String(e5);
        //substring(###)
        entity1=entity1.substring(0,entity1.indexOf("#"));
        entity2=entity2.substring(0,entity2.indexOf("#"));
        entity3=entity3.substring(0,entity3.indexOf("#"));
        entity4=entity4.substring(0,entity4.indexOf("#"));
        entity5=entity5.substring(0,entity5.indexOf("#"));
        //tfs
        int tf1=byteToInt(f1);
        int tf2=byteToInt(f2);
        int tf3=byteToInt(f3);
        int tf4=byteToInt(f4);
        int tf5=byteToInt(f5);

        Vector<Pair<String,Integer>> entities=new Vector();
        if(!entity1.equals(""))
            entities.add(new Pair(entity1,tf1));
        if(!entity2.equals(""))
            entities.add(new Pair(entity2,tf2));
        if(!entity3.equals(""))
            entities.add(new Pair(entity3,tf3));
        if(!entity4.equals(""))
            entities.add(new Pair(entity4,tf4));
        if(!entity5.equals(""))
            entities.add(new Pair(entity5,tf5));
        return entities;
    }


    /**
     * this class get query (string with spaces), parse it to final terms dictionary @link {@link Parser#_termList}.
     * for this dictionary<term,tf> this class return list of 50 most relevant documents using {@link Ranker}
     *
     *
     * @param id
     * @param query  - to retrieve
     * @param toTreatSemantic
     * @return list of relevant docs.
     */
    public Collection<Document> Search(String id, String query, boolean toTreatSemantic) {
        _doc_termPlusTfs = new HashMap<>();
        _doc_size = new HashMap<>();
        Collection<Document> docs= new Vector<>();
        isSemantic=toTreatSemantic;
        loadDictionaryToMemory(toStem); //using for "Entities"
        build_doc_termPlusTfs(query, toStem);
        FilterDocsByCitys();
        Collection<Integer>  docNums = _ranker.Rank(_doc_termPlusTfs, _doc_size, _numOfIndexedDocs, _term_docsCounter, _avgldl,_path); // return only 50 most relvante
        getEntities(docNums);
        Collection<String> docNames = docNumToNames(docNums,id);
        Iterator<Integer> docNumIt= docNums.iterator();
        Iterator<String> docNameIt= docNames.iterator();
        while (docNumIt.hasNext()) {
            Integer docNum= docNumIt.next();
            Vector<Pair<String, Integer>> entities;
            entities = _doc_Entities.get(docNum);
            docs.add(new Document(docNum,docNameIt.next(),entities));
        }
        return docs;
    }

    /**
     *
     * @param ans- doclines
     * @return name of documents
     */
    private Collection<String> docNumToNames(Collection<Integer> ans,String id) {
        //get doc names!
        Set<String> documentsToReturn=new HashSet<>();
        Map<Integer,Double> docLine_rank=_ranker.getDocsRanking();
        Map<String,Double> docs_rank=new HashMap<>();
        try {
            RandomAccessFile raf=new RandomAccessFile(new File(_path+"/Documents.txt"),"r");
            Iterator<Integer> docsIterator=ans.iterator();
            while(docsIterator.hasNext()){
                int lineNum=docsIterator.next();
                raf.seek(lineNum*54);
                byte [] nameInBytes=new byte[16];
                raf.read(nameInBytes);
                String name=convertByteToString(nameInBytes);
                documentsToReturn.add(name);
                docs_rank.put(name,docLine_rank.get(lineNum));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if(!_toSave.equals(""))
            WriteToQueryFile(docs_rank,id);
        return documentsToReturn;
    }

    /**
     *
     * @param docs_rank- the docs and thier ranks
     * @param id of query
     *  writes to the results file the result for the query
     */
    private void WriteToQueryFile(Map<String, Double> docs_rank,String id) {
        File file=new File(_toSave+"/results.txt");
        try {
            //if(!file.exists())
              //  file.createNewFile();
            BufferedWriter writer=new BufferedWriter(new FileWriter(file,true));
            Iterator<String> docs=docs_rank.keySet().iterator();
            while(docs.hasNext()){
                String document=docs.next();
                double rank=docs_rank.get(document);
                String rankInString=String.valueOf(rank);
                if(rankInString.length()>7)
                    rankInString=rankInString.substring(0,6);//not so much
                String line=id+"   "+0+"   "+document+"   "+rankInString+"   1   mt";
                writer.write(line);
                writer.newLine();
            }
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    /**
     *
     * @param name- in bytes
     * @return name in string
     */
    private String convertByteToString(byte[] name) {
        String s=new String(name, Charset.forName("UTF-8"));
        String out="";
        boolean flag=true;
        for (int i = 0; i < s.length()&&flag; i++) {
            if(s.charAt(i)!='#')
                out=out+s.charAt(i);
            else flag=false;
        }
        return out;
    }


    /**
     * remove from list of documents the documents that not in the list of citys from user
     */
    private void FilterDocsByCitys() {
        try {
            RandomAccessFile raf=new RandomAccessFile(_path+"/Documents.txt","r");
            Vector<Integer> toDelete=new Vector<>();
            Iterator<Integer> iterator=_doc_termPlusTfs.keySet().iterator();
            while(iterator.hasNext()){
                Integer docLine=iterator.next();
                //filter stage
                if(_chosenCities.size()>0) {
                    raf.seek(docLine * 54 + 16);//to get to the city
                    byte[] city_bytes = new byte[16];
                    raf.read(city_bytes);
                    String city  = new String(city_bytes, StandardCharsets.UTF_8);
                    if(city.contains("#"))
                        city = city.substring(0, city.indexOf("#"));
                    if(city.equals("")||!_chosenCities.contains(city)){
                        toDelete.add(docLine);
                    }
                }
            }
            //delete all the toDelete
            for(int i=0;i<toDelete.size();i++){
                int lineNum=toDelete.get(i);
                _doc_termPlusTfs.remove(lineNum);
            }
            iterator=_doc_termPlusTfs.keySet().iterator();//all the remain ones
            //add to each doc it's size
            while (iterator.hasNext()){
                int docLine=iterator.next();
                raf.seek(docLine * 54 + 50);//to get to the size
                byte[] length_bytes=new byte[4];
                raf.read(length_bytes);
                int size=byteToInt(length_bytes);
                _doc_size.put(docLine,size);
            }
            raf.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}



