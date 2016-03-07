package lazynotes;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.cortical.retina.client.FullClient;
import io.cortical.retina.core.Compare;
import io.cortical.retina.model.Metric;
import io.cortical.retina.model.Text;
import io.cortical.retina.rest.ApiException;
import org.canova.api.util.ClassPathResource;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.text.sentenceiterator.LineSentenceIterator;
import org.deeplearning4j.text.sentenceiterator.SentenceIterator;
import org.deeplearning4j.text.sentenceiterator.SentencePreProcessor;
import org.deeplearning4j.text.tokenization.tokenizer.TokenPreProcess;
import org.deeplearning4j.text.tokenization.tokenizer.preprocessor.EndingPreProcessor;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by Joanne on 2016-02-27.
 */
public class Driver {
    private static final String API_KEY  = "87d532a0-ddba-11e5-8378-4dad29be0fab";

    public static String readFile(String path) throws IOException {
        Path p = Paths.get(path);
        System.out.println(p.toAbsolutePath());
        File file = new File(path);
        FileReader fr = new FileReader(file);
        BufferedReader bufferedReader = new BufferedReader(fr);
        StringBuffer sb = new StringBuffer();
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            sb.append(line);
            sb.append(" ");
        }
        fr.close();
        return sb.toString();
    }

    public static List<Integer> compareText(String s1, List<String> list2) {


        return null;
    }

    public static class Chunk {
        public String body;
        public int position;

        public  Chunk(String sentence, int pos) {
            body = sentence;
            position = pos;
        }
    }

    public static class ChunkComparison implements Comparable<ChunkComparison>{
        public Chunk chunk1;
        public Chunk chunk2;
        public Metric metric;

        public ChunkComparison(Chunk c1, Chunk c2, Metric m) {
            this.chunk1=c1;
            this.chunk2=c2;
            this.metric=m;
        }

        public double getStrength(){
            return this.metric.getCosineSimilarity();
        }


        @Override
        public int compareTo(ChunkComparison o) {
            return -1*Double.compare(this.getStrength(), o.getStrength());
        }
    }

    public static List<Chunk> chunkify(String s) {
        //TODO: Find regex for matching punctuation
        List<String> sentences = new ArrayList<String>(Arrays.asList(s.split("[\\.!\\?\\n]")));
        int pos = 0;
        List<Chunk> result = new ArrayList<Chunk>();
        String acc="";
        for (String sentence : sentences) {
        	acc+= sentence.trim();
        	int newpos = pos+sentence.length();
        	if (newpos+1<s.length())
        		acc+=". ";//s.charAt(newpos)+s.charAt(newpos+1);
        	if (acc.length()>40) {
        		Chunk chunk = new Chunk(acc, pos);
        		result.add(chunk);
	            acc="";
        	}
            pos =newpos+1;
        }
        if (acc.length()>3) {
        	Chunk chunk = new Chunk(acc, pos);
        	result.add(chunk);
        }
        return result;
    }

    public static List<List<ChunkComparison>> bulkCompare(List<Chunk> chunk1,  List<Chunk> chunk2) throws JsonProcessingException, ApiException {
        FullClient client = new FullClient(API_KEY, "en_associative");

        List<Compare.CompareModel> compareModelList = new ArrayList<Compare.CompareModel>();

        for (Chunk c1 : chunk1)
            for (Chunk c2 : chunk2) {
                compareModelList.add(new Compare.CompareModel(new Text(c1.body.replace('\n', ' ')),new Text(c2.body.replace('\n', ' '))));
            }
        System.out.println(compareModelList);
        System.out.println(compareModelList.get(0).getModel1().toJson());
        System.out.println(compareModelList.get(0).getModel2().toJson());
        Metric[] metrics = client.compareBulk(compareModelList);
        List<List<ChunkComparison>> comparisons =  new ArrayList<List<ChunkComparison>>();
        int i=0;
        for (Chunk c1 : chunk1) {
            ArrayList<ChunkComparison> chunk1comparisons = new ArrayList<ChunkComparison>();
            for (Chunk c2 : chunk2) {
                chunk1comparisons.add(new ChunkComparison(c1, c2, metrics[i]));
                i++;
            }
            comparisons.add(chunk1comparisons);
        }
        return comparisons;
    }

    public enum StepType {
        LEFT,
        RIGHT,
        BOTH,
        DONE
    }

    public static class Link {
        public int p1;
        public int p2;
        public double strength_pt;
    }
    public static class Accordian {
        ArrayList<Link> links;
        public Accordian() {
            links = new ArrayList<Link>();
        }
    }

    public static class DynamicTracebackResult {
        public double strength_acc;
        public StepType step;
    }

    //last argument is memoization table
    public static DynamicTracebackResult getMaxStrength( List<List<ChunkComparison>> comparisons, int index1, int index2, DynamicTracebackResult[][] m){
        DynamicTracebackResult dts = new DynamicTracebackResult();
        if (m[index1][index2]!=null)
            return m[index1][index2];
        dts.strength_acc =comparisons.get(index1).get(index2).getStrength();
        int size1 = comparisons.size();
        int size2 = comparisons.get(index1).size();
        if (size1-index1==1&&size2-index2==1) {
            dts.step=StepType.DONE;
        } else if (size1-index1==1) {
            dts.step=StepType.RIGHT;
            dts.strength_acc +=getMaxStrength(comparisons,index1,index2+1,m).strength_acc;
        } else if (size2-index2==1) {
            dts.step=StepType.LEFT;
            dts.strength_acc +=getMaxStrength(comparisons,index1+1,index2,m).strength_acc;
        } else {
            DynamicTracebackResult left = getMaxStrength(comparisons,index1+1,index2,m);
            DynamicTracebackResult right = getMaxStrength(comparisons,index1,index2+1,m);
            DynamicTracebackResult both = getMaxStrength(comparisons,index1+1,index2+1,m);
            //this is a hack
            if (left.strength_acc >right.strength_acc) {
                if (left.strength_acc > both.strength_acc) {
                    dts.strength_acc += left.strength_acc;
                    dts.step = StepType.LEFT;
                } else {
                    dts.strength_acc += both.strength_acc;
                    dts.step = StepType.BOTH;
                }
            } else {
                if (right.strength_acc > both.strength_acc)  {
                    dts.strength_acc += right.strength_acc;
                    dts.step = StepType.RIGHT;
                } else {
                    dts.strength_acc += both.strength_acc;
                    dts.step = StepType.BOTH;
                }
            }
        }
        m[index1][index2]=dts;
        return dts;
    }

    public static Accordian makeAccordian( List<List<ChunkComparison>> comparisons){
        //memoization table:
        DynamicTracebackResult[][] m = new DynamicTracebackResult[comparisons.size()][comparisons.get(0).size()];
        getMaxStrength(comparisons,0,0,m);
        //retrace steps through memoization table:
        Accordian a = new Accordian();
        int p1=0;
        int p2=0;
        while (true) {
            Link l = new Link();
            l.strength_pt = comparisons.get(p1).get(p2).getStrength();
            l.p1=p1;
            l.p2=p2;
            a.links.add(l);
            switch (m[p1][p2].step){
                case LEFT:
                    p1++;
                    break;
                case RIGHT:
                    p2++;
                    break;
                case BOTH:
                    p1++;
                    p2++;
                    break;
                default:
                    return a;
            }
        }
    }

    public static enum CorrelationType {
        STRONG,
        WEAK
    }

    public static String mergeChunks(List<Chunk> c1,List<Chunk> c2){
        List<Chunk> bigger = c1;
        if (c2.size()>c1.size())
            bigger=c2;
        return chunkListToString(bigger);
    }

    public static String chunkListToString(List<Chunk> chunks) {
        String returnval="";
        for (Chunk c : chunks) {
            returnval=returnval+c.body.trim()+" ";
        }
        return returnval;
    }
    public static final int MIN_ACCU_NOTES_LENGTH = 1000;

    public static double getCorpusCos(int p1, int p2, List<Chunk> chunk1, List<Chunk> chunk2, Word2Vec vec) {
        String b1 = chunk1.get(p1).body;
        String b2 = chunk2.get(p2).body;
        String[] b1split = b1.split(" ");
        String[] b2split = b2.split(" ");
        List<Float> sims = new ArrayList<Float>();
        for (String s : b1split) {
            double highestSim = getMaxSim(s, b2split, vec);
        }
        double acc = 0;
        for (Float f : sims) {
            acc += f;
        }
        return acc/sims.size();
    }

    public static double getMaxSim(String word, String[] b2split, Word2Vec vec) {
        double max = 0;
        for (String s : b2split) {
            double m = vec.similarity(word, s);
            if (m > max) {
                max = m;
            }
        }
        return max;
    }
    public static void main(String[] args) throws IOException, ApiException {
        //String n1 = readFile(args[0]);
        //String n2 = readFile(args[1]);
        String n1 = readFile("Note1.txt");
        String n2 = readFile("Note2.txt");
        //add contents of note1 and note2 if not exists
        //if length of accumulated notes is less than 10000 continue with what we have
        //otherwise: say two notes agree only if both metrics are above threshhold
        //String accu_notes = readFile("accumulatedNotes.txt");

        String accu_notes = "";
        boolean incorporateNoteData = false;
        Word2Vec vec = null;
        if (accu_notes.length() > MIN_ACCU_NOTES_LENGTH) {
            incorporateNoteData = true;
            System.out.println("READING");
            ClassPathResource resource = new ClassPathResource("accumulated_notes.txt");
            SentenceIterator iter = new LineSentenceIterator(resource.getFile());
            iter.setPreProcessor(new SentencePreProcessor() {
                @Override
                public String preProcess(String sentence) {
                    return sentence.toLowerCase();
                }
            });
            System.out.println("TOKENIZING");
            final EndingPreProcessor preProcessor = new EndingPreProcessor();
            TokenizerFactory tokenizer = new DefaultTokenizerFactory();
            tokenizer.setTokenPreProcessor(new TokenPreProcess() {
                @Override
                public String preProcess(String token) {
                    token = token.toLowerCase();
                    String base = preProcessor.preProcess(token);
                    base = base.replaceAll("\\d", "d");
                    if (base.endsWith("ly") || base.endsWith("ing"))
                        System.out.println();
                    return base;
                }
            });

            int batchSize = 1000;
            int iterations = 3;
            int layerSize = 150;

            System.out.println("Building model");
            vec = new Word2Vec.Builder()
                    .batchSize(batchSize) //# words per minibatch.
                    .minWordFrequency(5) //
                    .useAdaGrad(false) //
                    .layerSize(layerSize) // word feature vector size
                    .iterations(iterations) // # iterations to train
                    .learningRate(0.025) //
                    .minLearningRate(1e-3) // learning rate decays wrt # words. floor learning
                    .negativeSample(10) // sample size 10 words
                    .iterate(iter) //
                    .tokenizerFactory(tokenizer)
                    .build();
            vec.fit();
        }



        System.out.println(n1);
        System.out.println(n2);

        List<Chunk> chunk1 = chunkify(n1);
        chunk1=chunk1.subList(0, Math.min(10,chunk1.size()));
        List<Chunk> chunk2 = chunkify(n2);
        chunk2=chunk2.subList(0, Math.min(10,chunk2.size()));

        List<List<ChunkComparison>> comparisons = bulkCompare(chunk1,chunk2);

        Accordian a = makeAccordian(comparisons);
        for (Link l : a.links) {
            System.out.println("LINK: " + l.p1 + "-" + l.p2);
            System.out.println("   strength_pt: " + l.strength_pt);
            System.out.println("   source1: " + chunk1.get(l.p1).body);
            System.out.println("   source2: " + chunk2.get(l.p2).body);
        }
        System.out.println("________________");
        Double STRENGTH_THRESHHOLD_WEAK = 0.6;
        Double STRENGTH_THRESHHOLD_STRONG = 0.6;
        CorrelationType mode = CorrelationType.WEAK;
        int pstartblock1=-1;
        int pstartblock2=-1;


        String mergeResult="";
        String SEPARATOR = "\n";

        Link prev = null;
        for (Link l : a.links){
            System.out.print(mode);
            System.out.println(" "+pstartblock1+","+pstartblock2+" ["+l.p1 +":"+l.p2+"]");
            switch (mode) {
                case STRONG:
                    if (l.strength_pt<STRENGTH_THRESHHOLD_WEAK) {
                        mergeResult+=mergeChunks(chunk1.subList(pstartblock1,l.p1),
                                                chunk2.subList(pstartblock2,l.p2))+SEPARATOR;
                        pstartblock1=l.p1;
                        pstartblock2=l.p2;
                        mode=CorrelationType.WEAK;
                    }
                    break;
                case WEAK:
                    if (incorporateNoteData==false) {
                        if (l.strength_pt>STRENGTH_THRESHHOLD_STRONG) {
                            System.out.println("diffchunk: [" + pstartblock1 + ":"+l.p1 + ", " + pstartblock2 + ":" + l.p2 + "]");
                            //take range (pstartblock1,prev.p1) and (pstartblock2,prev.p2) and place sequentially
                            String add="";
                            if (pstartblock1+1<l.p1)
                                add+=chunkListToString(chunk1.subList(pstartblock1+1,l.p1))+SEPARATOR;
                            if (pstartblock2+1<l.p2)
                                add+=chunkListToString(chunk2.subList(pstartblock2+1,l.p2))+SEPARATOR;
                            mergeResult+=add;
                            System.out.println(add);
                            pstartblock1=l.p1;
                            pstartblock2=l.p2;
                            mode=CorrelationType.STRONG;
                        }
                    } else {
                        double corpusStrengthPt = getCorpusCos(l.p1, l.p2, chunk1, chunk2, vec);
                        if (l.strength_pt>STRENGTH_THRESHHOLD_STRONG && corpusStrengthPt>STRENGTH_THRESHHOLD_STRONG) {
                            System.out.println("diffchunk: [" + pstartblock1 + ":"+l.p1 + ", " + pstartblock2 + ":" + l.p2 + "]");
                            //take range (pstartblock1,prev.p1) and (pstartblock2,prev.p2) and place sequentially
                            String add="";
                            if (pstartblock1+1<l.p1)
                                add+=chunkListToString(chunk1.subList(pstartblock1+1,l.p1))+SEPARATOR;
                            if (pstartblock2+1<l.p2)
                                add+=chunkListToString(chunk2.subList(pstartblock2+1,l.p2))+SEPARATOR;
                            mergeResult+=add;
                            //System.out.println(add);
                            pstartblock1=l.p1;
                            pstartblock2=l.p2;
                            mode=CorrelationType.STRONG;
                        }
                    }

            }
            prev=l;
        }
        if (mode==CorrelationType.STRONG) {
            //take range [pstartblock1,prev.p1] and [pstartblock2,prev.p2] and merge
            String mergedText = mergeChunks(chunk1.subList(pstartblock1,prev.p1+1),
                    chunk2.subList(pstartblock2,prev.p2+1));
            mergeResult+=mergedText+SEPARATOR;
        }



        System.out.println(mergeResult);
        //String outputFile = args[2];
        //PrintWriter writer = new PrintWriter(args[2], "UTF-8");
        //writer.write(mergeResult);
        //writer.close();
    }





}