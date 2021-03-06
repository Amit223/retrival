import com.sun.scenario.effect.impl.sw.sse.SSEBlend_SRC_OUTPeer;
import javafx.stage.Stage;
import javafx.util.Pair;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import sun.awt.Mutex;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


public class Model {



    private Indexer indexer;
    /**
     *
     * @return true if load of dictionary to memort successful and false otherwise
     */
    public boolean loadDictionaryToMemory(){
        return indexer.loadDictionaryToMemory();
    }

    /**
     * delete all the posting files from the results
     * folder using {@link Indexer#delete()}
     * @return true if reset successful and false otherwise
     */
    public boolean Reset(){
        return indexer.delete();
    }

    /**
     * using {@link Indexer#getNumberOfDocs()}
     * @return number of documents indexed
     */
    public int getNumberOfDocs(){
        return indexer.getNumberOfDocs();
    }

    /**
     * using {@link Indexer#getNumberOfTerms()}
     * @return number of unique terms
     */
    public int getNumberOfTerms(){
        return indexer.getNumberOfTerms();
    }

    /**
     * using {@link Indexer#getLanguages()}
     * @return the languages of files found
     */
    public Set<String> getLanguages(){
        return indexer.getLanguages();
    }

    /**
     * using {@link #getDictionary()}
     * @return dictionary
     */
    public Map<String, Vector<Integer>> getDictionary() {
        return indexer.getDictionary();
    }

    /**
     * This function is the main function of the program.
     * First, use {@link ReadFile} to set {@link StopWords}.
     * and then, use {@link ThreadedIndex} that that do
     * the read file, parse and index processes parallelity.
     * it also load program's dictionaries to files in memory
     * and sort the posting files.
     * @param toStem - get from the {@link Controller}
     * @param path of corpus and stop words
     * @param toSave- path that posting files will be saved in
     */
    public void Start(boolean toStem,String path,String toSave){
        indexer=new Indexer(toStem,toSave);
        String s=ReadFile.readStopWords(path);
        StopWords.setStopwords(s);
        ExecutorService pool= Executors.newFixedThreadPool(1);
        File folder = new File(path);
        File[] listOfFiles = folder.listFiles();

        for(int i=0; i<listOfFiles.length;i++){
            if(listOfFiles[i].isDirectory()){//the corpus!!
                File[] directories=listOfFiles[i].listFiles();//all the folders in corpus
                Thread [] threads=new ThreadedIndex[directories.length];
                for(int j=0;j<directories.length;j++){
                    threads[j]=new ThreadedIndex(directories[j].getAbsolutePath(),toStem,indexer);
                    pool.execute(threads[j]);

                }


                pool.shutdown();

                try {
                    boolean flag = false;
                    while (!flag)
                        flag = pool.awaitTermination(1009, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
        }
        indexer.push();
        System.out.println("done part a");
        indexer.loadDictionaryToFile();
        indexer.loadCityDictionaryToFile();
        StopWords.reset();
        indexer.sort();
        System.out.println("done part b-sort");
        indexer.loadDictionaryToMemory();
        indexer.UpdateDictionary();
        indexer.loadDictionaryToFile();
        System.out.println("done part c- dictionary updated");



    }
}

/**
 * used for the {@link #start()}
 * this class extend thread and implements a thread that:
 * read file using {@link ReadFile}
 * get the relevant information from the tags
 * parse the text to #termList
 * and index the files using it.
 *
 */
class ThreadedIndex extends Thread{

    private String path; // the path of the documents to read files.
    private boolean toStem; // if the user want  to stem = true
    private Indexer indexer; //the indexer of the program

    /**
     * constructor
     * @param path of the file
     * @param toStem
     * @param indexer
     *
     */
    public ThreadedIndex(String path, boolean toStem,Indexer indexer) {
        this.path = path;
        this.toStem = toStem;
        this.indexer=indexer;
    }

    /**
     *
     * this function:
     * read file using {@link ReadFile}
     * get the relevant information from the tags
     * <TEXT></TEXT> <DOCNO></DOCNO> <F></F> <P 104></P> <P 105></P>
     * seprate to douments.
     * parse the text to #termList using {@link Parser}
     * and index the files using it by {@link Indexer}
     */
    public void run(){
        ReadFile.read(path);
        Elements elements=ReadFile.getDocs();
        HashMap<String, Integer> termList;
        for(int i=0;i<elements.size();i++){
            if(elements.get(i)!=null&&elements.get(i).getElementsByTag("TEXT")!=null) {
                String text = elements.get(i).getElementsByTag("TEXT").text();
                String name = elements.get(i).getElementsByTag("DOCNO").text();
                Elements Felements = elements.get(i).getElementsByTag("F");
                String city = "";
                String language = "";
                for (Element element1 : Felements) {
                    if (element1.attr("P").equals("104")) {//city
                        city = element1.text();
                        if (city.contains(" "))
                            city = city.substring(0, city.indexOf(" "));
                    } else if (element1.attr("P").equals("105"))//language
                    {
                        language = element1.text();
                    }
                }
                Parser parser = new Parser();
                parser.Parse(text, toStem, city);//return termlist
                termList = parser.getTerms();
                int numOfWords=parser.getWordCount();
                indexer.Index(termList, parser.getLocations(), name, city, numOfWords, language);
            }
        }
    }
}