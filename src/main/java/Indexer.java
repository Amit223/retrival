import javafx.util.Pair;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Indexer {

    private Map<String,Pair<Integer,Integer>> dictionary;
    private File posting;//lineNum(4 bytes)|tf(4 Bytes)|pte next(4 Bytes) ==12 bytes
    private int lineNumPosting;
    private File documents;//docName(20 bytes)|city(18)|maxtf(4)|num of terms(4)|words(4)-50 bytes
    private int lineNumDocs;

    //citys
    private Map <String,Pair<Vector<String>,Integer>> cityDictionary;
    private File citysPosting;//docLine(4 B)|loc1|loc2|loc3|loc4|loc5|ptr nxt==28 bytes (4 each)
    private int lineNumCitys;

    public Indexer() {
        try {
            dictionary=new HashMap<>();
            posting = new File("Posting.txt");//docline(4)|tf(4)|pointer(4)-12 bytes
            posting.createNewFile();
            lineNumPosting=0;
            documents=new File("Documents.txt");//docName(20 bytes)|city(18)|maxtf(4)|num of terms(4)|words(4)-50 bytes
            lineNumDocs=0;

            //citys
            cityDictionary =new HashMap<>();
            citysPosting = new File("CityPosting.txt");
            citysPosting.createNewFile();
            lineNumCitys=0;
        }
        catch (IOException e){

        }
    }

    public void Index(Dictionary<String,Integer> terms,Vector<Integer> locations,String nameOfDoc,String cityOfDoc,
                      int numOfWords){
        Enumeration<String> keys=terms.keys();
        int maxtf=getMaxTf(terms.elements());
        writeToDocuments(nameOfDoc,cityOfDoc,maxtf,terms.size(),numOfWords);
        while (keys.hasMoreElements()) {
            String term = keys.nextElement();
            int lineOfFirstDoc = toDictionaryFile(term);
            if(lineOfFirstDoc!=lineNumPosting)
                searchInPosting(lineOfFirstDoc);//updates the term's ladt doc that new line will be added in lineNumPosting
            writeToPosting(lineNumDocs,terms.get(term));
            lineNumPosting+=1;
        }
        toStatesDictionary(cityOfDoc,"","","");//todo- get function from http-complete html request
        toCityPosting(locations);

        lineNumDocs+=1;
        lineNumCitys+=1;
    }


    public Map<String, Pair<Integer, Integer>> getDictionary() {
        return dictionary;
    }
    public Map<String, Pair<Vector<String>, Integer>> getCityDictionary() {
        return cityDictionary;
    }

    /**
     * delete the files text
     */
    public void delete(){
        try {
            PrintWriter writer = new PrintWriter(posting);
            writer.print("");
            writer.close();
             writer = new PrintWriter(documents);
            writer.print("");
            writer.close();
            writer=new PrintWriter(citysPosting);
            writer.print("");
            writer.close();
        }
        catch (Exception e){
            System.out.println("problem in indexer->delete");
        }
    }

    /**
     *
     * @param lineInPosting
     * updates the pointer of last doc of the city
     */


    /**
     *
     * @param locations
     *
     * adds all vector of location to the state posting
     */
    private void toCityPosting(Vector<Integer> locations) {
        int size=locations.size();
        int index=0;
        byte[] toWrite=new byte[28];
        byte [] docline=toBytes(this.lineNumDocs);
        byte []loc1;
        byte []loc2;
        byte []loc3;
        byte []loc4;
        byte []loc5;
        byte[]ptr;
        while(size>5){
            loc1=toBytes(locations.get(index*5));
            loc2=toBytes(locations.get(index*5+1));
            loc3=toBytes(locations.get(index*5+2));
            loc4=toBytes(locations.get(index*5+3));
            loc5=toBytes(locations.get(index*5+4));
            ptr=toBytes(this.lineNumCitys);
            for (int i = 0; i < 4; i++) {
                toWrite[i]=docline[i];
            }
            for (int i = 4; i <8 ; i++) {
                toWrite[i]=loc1[i-4];
            }

            for (int i = 8; i < 12; i++) {
                toWrite[i]=loc2[i-8];
            }
            for (int i = 12; i <16 ; i++) {
                toWrite[i]=loc3[i-12];
            }

            for (int i = 16; i < 20; i++) {
                toWrite[i]=loc4[i-16];
            }
            for (int i = 20; i <24 ; i++) {
                toWrite[i]=loc5[i-20];
            }

            for (int i = 24; i < 28; i++) {
                toWrite[i]=ptr[i-24];
            }
            try {
                RandomAccessFile raf=new RandomAccessFile(citysPosting,"rw");
                raf.seek(raf.length());
                raf.write(toWrite);
                raf.close();
                this.lineNumCitys+=1;
            }
            catch (Exception e){
                System.out.println("problem in write to city posting");
            }
            index+=1;
            size=size-5;
        }
        loc1=toBytes(-1);
        loc2=toBytes(-1);
        loc3=toBytes(-1);
        loc4=toBytes(-1);
        loc5=toBytes(-1);
        ptr=toBytes(-1);
        if(size>0)
            loc1=toBytes(locations.get(index*5));
        if(size>1)
            loc2=toBytes(locations.get(index*5+1));
        if(size>2)
            loc3=toBytes(locations.get(index*5+2));
        if(size>3)
            loc4=toBytes(locations.get(index*5+3));
        if(size>4)
            loc5=toBytes(locations.get(index*5+4));
        for (int i = 0; i < 4; i++) {
            toWrite[i]=docline[i];
        }
        for (int i = 4; i <8 ; i++) {
            toWrite[i]=loc1[i-4];
        }

        for (int i = 8; i < 12; i++) {
            toWrite[i]=loc2[i-8];
        }
        for (int i = 12; i <16 ; i++) {
            toWrite[i]=loc3[i-12];
        }

        for (int i = 16; i < 20; i++) {
            toWrite[i]=loc4[i-16];
        }
        for (int i = 20; i <24 ; i++) {
            toWrite[i]=loc5[i-20];
        }

        for (int i = 24; i < 28; i++) {
            toWrite[i]=ptr[i-24];
        }
        try {
            RandomAccessFile raf=new RandomAccessFile(citysPosting,"rw");
            raf.seek(raf.length());
            raf.write(toWrite);
            raf.close();
        }
        catch (Exception e){
            System.out.println("problem in write to city posting");
        }


    }

    private void htmlRequest(String cityOfDoc){
        try {
            DefaultHttpClient httpClient = new DefaultHttpClient();
            HttpGet getRequest = new HttpGet(
                    "https://restcountries.eu/rest/v2/capital/"+cityOfDoc+"?fields=name/get");
            getRequest.addHeader("accept", "application/json");
            HttpResponse response = httpClient.execute(getRequest);
            if (response.getStatusLine().getStatusCode() != 200) {
                return;
            }
            BufferedReader br = new BufferedReader(
                    new InputStreamReader((response.getEntity().getContent())));

            String output;
            while ((output = br.readLine()) != null) {
                System.out.println(output);
            }

            httpClient.getConnectionManager().shutdown();

        }
        catch (IOException e){
            System.out.println("ffffff");
        }


    }
    private void toStatesDictionary(String cityOfDoc,String country,String coin,String population) {
        if(!cityDictionary.containsKey(cityOfDoc)){
            Vector<String> v=new Vector<>();
            v.add(country);
            v.add(coin);
            v.add(population);
            Pair <Vector<String>,Integer>pair=new Pair<>(v,new Integer(this.lineNumCitys));
            cityDictionary.put(cityOfDoc,pair);
        }
        else{//exist need to update the posting file's pointer
            int lineInPosting=(cityDictionary.get(cityOfDoc)).getValue();
            updateStatesPosting(lineInPosting);
        }


    }


    private void updateStatesPosting(int lineInPosting) {
        try {
            RandomAccessFile raf = new RandomAccessFile(citysPosting, "rw");
            raf.seek(28*lineInPosting+24);
            byte[] ptr = new byte[4];
            raf.read(ptr);
            int ptr_int=byteToInt(ptr);
            int prevptr=lineInPosting;//to know what line is the last of the term's docs
            while(ptr_int!=-1){
                prevptr=ptr_int;
                raf.seek(ptr_int*27+24);
                ptr = new byte[4];
                raf.read(ptr);
                ptr_int=byteToInt(ptr);
            }
            //the last line.need to change the pointer!
            raf.seek(prevptr*28);
            byte[] line = new byte[28];
            raf.read(line);
            ptr=toBytes(this.lineNumCitys);
            line[46]=ptr[0];
            line[47]=ptr[1];
            line[48]=ptr[2];
            line[49]=ptr[3];
            raf.close();

        }
        catch(Exception e){

        }
    }


    private void writeToPosting(int lineOfDoc,int tf) {
        byte [] tf_bytes=toBytes(tf);
        byte [] line_bytes=toBytes(lineOfDoc);
        byte [] ptr_bytes=toBytes(-1);

        byte [] toWrite=new byte[12];
        for (int i = 0; i < 4; i++) {
            toWrite[i]=line_bytes[i];
        }
        for (int i = 4; i <8 ; i++) {
            toWrite[i]=tf_bytes[i-4];
        }

        for (int i = 8; i < 12; i++) {
            toWrite[i]=ptr_bytes[i-8];
        }
        try {
            RandomAccessFile raf=new RandomAccessFile(posting,"rw");
            raf.seek(raf.length());
            raf.write(toWrite);
            raf.close();
        }
        catch (Exception e){
            System.out.println("problem in writeToPosting");
        }

    }

    /**
     *
     * @param nameOfDoc
     * @param cityOfDoc
     * @param maxtf
     * @param size
     * @param numOfWords
     *
     * writes to the docs file the document details-
     * docName(20 bytes)|city(18)|maxtf(4)|num of terms(4)|words(4)-50 bytes

     */
    private void writeToDocuments(String nameOfDoc, String cityOfDoc, int maxtf, int size, int numOfWords) {
        //docName(20 bytes)|city(18)|maxtf(4)|num of terms(4)|words(4)-50 bytes
        byte[] name=stringToByteArray(nameOfDoc,20);
        byte[] city=stringToByteArray(cityOfDoc,18);
        byte [] maxtf_bytes=toBytes(maxtf);
        byte [] size_bytes=toBytes(size);
        byte [] words_bytes=toBytes(numOfWords);
        byte [] toWrite=new byte[50];
        for (int i = 0; i < 20; i++) {
            toWrite[i]=name[i];
        }
        for (int i = 20; i <38 ; i++) {
            toWrite[i]=city[i-20];
        }

        for (int i = 38; i < 42; i++) {
            toWrite[i]=maxtf_bytes[i-38];
        }
        for (int i = 42; i <46 ; i++) {
            toWrite[i]=size_bytes[i-42];
        }

        for (int i = 46; i < 50; i++) {
            toWrite[i]=words_bytes[i-46];
        }
        try {
            RandomAccessFile raf=new RandomAccessFile(documents,"rw");
            raf.seek(raf.length());
            raf.write(toWrite);
            raf.close();
        }
        catch (Exception e){
            System.out.println("problem in write to doc");
        }

    }

    /**
     *
     * @param lineOfFirstDoc
     * searches for last row of docs of the term, updates its pointer to the new row
     */
    private void searchInPosting(int lineOfFirstDoc) {
        try {
            RandomAccessFile raf = new RandomAccessFile(posting, "r");
            raf.seek(lineOfFirstDoc*12+8);
            byte[] ptr = new byte[4];
            raf.read(ptr);
            int ptr_int=byteToInt(ptr);
            int prevptr=lineOfFirstDoc;//to know what line is the last of the term's docs
            while(ptr_int!=-1){
                prevptr=ptr_int;
                raf.seek(ptr_int*12+8);
                ptr = new byte[4];
                raf.read(ptr);
                ptr_int=byteToInt(ptr);
            }
            //the last line.need to change the pointer!
            raf.seek(prevptr*12);
            byte[] line = new byte[12];
            raf.read(line);
            raf.close();
            ptr=toBytes(this.lineNumPosting);
            line[8]=ptr[0];
            line[9]=ptr[1];
            line[10]=ptr[2];
            line[11]=ptr[3];
            //updates the line
            raf=new RandomAccessFile(posting,"rw");
            raf.seek(prevptr*12);
            raf.write(line);
            raf.close();
        }
        catch (Exception e){
            System.out.println("problem in searchInPosting !");
        }

    }

    private int toDictionaryFile(String term) {
        if(dictionary.containsKey(term)){//just add to df and return the line in posting
           int df= dictionary.get(term).getKey();
           int pointer= dictionary.get(term).getValue();
           df+=1;
           dictionary.remove(term);
           dictionary.put(term,new Pair<Integer, Integer>(df,pointer));
           return pointer;
        }
        else if(dictionary.containsKey(Reverse(term))){//the reversed term in dictionary need to put the uppercase one
            String newTerm;
            if(Character.isUpperCase(Reverse(term).charAt(0))){//the term in dictionary is the uppercase one
                newTerm=Reverse(term);
            }
            else{//the new term is the upper case one!
                newTerm=term;
            }
            int df= dictionary.get(Reverse(term)).getKey();
            int pointer= dictionary.get(Reverse(term)).getValue();
            df+=1;
            dictionary.remove(Reverse(term));
            dictionary.put(newTerm,new Pair<Integer, Integer>(df,pointer));
            return pointer;
        }
        else {//new term completely
            dictionary.put(term,new Pair<Integer, Integer>(1,lineNumPosting));
            return lineNumPosting;
        }
        /**

            int []lineInDic= getLineInDic(term);
            try {
                if (lineInDic[0] == 1) {//is in dictionary
                    RandomAccessFile randomAccessFile = new RandomAccessFile(dictionary, "rw");
                    randomAccessFile.seek(lineInDic[1] * 40);
                    byte[] termInBytes = new byte[32];
                    randomAccessFile.seek(lineInDic[1] * 40 + 32);
                    byte[] idfInBytes = new byte[4];
                    randomAccessFile.read(idfInBytes);
                    randomAccessFile.seek(lineInDic[1] * 40 + 32 + 4);
                    byte[] pointerInBytes = new byte[4];
                    randomAccessFile.read(pointerInBytes);
                    //byte[]->string
                    String termFromDic = new String(termInBytes, StandardCharsets.UTF_8);
                    //get the int value of ptr,idf
                    int idf_int = byteToInt(idfInBytes);
                    int ptr_int = byteToInt(pointerInBytes);
                    idf_int += 1;//another doc

                    String newTerm = getRealTerm(term, termFromDic);
                    randomAccessFile.close();
                    AddToDic(newTerm, idf_int, ptr_int, lineInDic[1], 1);
                    this.lineNumDic = this.lineNumDic += 1;
                    return ptr_int;

                } else//new term!
                {
                    int idf_int = 1;
                    int ptr_int = this.lineNumPosting;
                    AddToDic(term, idf_int, ptr_int, lineInDic[1], 0);
                    this.lineNumDic += 1;
                    return ptr_int;
                }
            }
            catch (Exception e){
                System.out.println("problem in indexer->toDictionaryFile");
            }
         **/

    }

    private String Reverse(String term) {
        int offset = 'a' - 'A';
        char c=term.charAt(0);
        if(c >= 'A' && c <= 'Z'){//turn to small
            term=term.toLowerCase();
        }
        if((c >= 'a' && c <= 'z'))//turn to big letters
        {
            term=term.toUpperCase();
        }
        return term;
    }


    public int byteToInt(byte[] bytes) {
        int val = 0;
        for (int i = 0; i < 4; i++) {
            val=val<<8;
            val=val|(bytes[i] & 0xFF);
        }
        return val;
    }
    private byte[] toBytes(int i)
    {
        byte[] result = new byte[4];

        result[0] = (byte) (i >> 24);
        result[1] = (byte) (i >> 16);
        result[2] = (byte) (i >> 8);
        result[3] = (byte) (i /*>> 0*/);

        return result;
    }


    private int getMaxTf(Enumeration<Integer> elements) {
        int max=elements.nextElement();
        while(elements.hasMoreElements()){
            int num=elements.nextElement();
            if(num>max)
                max=num;
        }
        return max;
    }

    /**
     *
     * @param term
     * @return converts string into byte array of size 30
     */

    private byte[] stringToByteArray(String term,int length){
        byte [] stringInByte=term.getBytes(StandardCharsets.UTF_8);
        byte [] fullByteArray=new byte[length];
        for (int i = 0; i<length ; i++) {
            if(i<stringInByte.length)
                fullByteArray[i]=stringInByte[i];
            else
                fullByteArray[i]=35;//# in ascii
        }
        return fullByteArray;

    }


}