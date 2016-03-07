package lazynotes;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.cortical.retina.client.FullClient;
import io.cortical.retina.core.Compare;
import io.cortical.retina.core.PosType;
import io.cortical.retina.model.*;
import io.cortical.retina.rest.ApiException;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Joanne on 2016-02-27.
 */
public class PracticeCortical {
    private static final String API_KEY  = "87d532a0-ddba-11e5-8378-4dad29be0fab";

    public void testAPI() throws ApiException, JsonProcessingException {
        FullClient client = new FullClient(API_KEY, "en_associative");
        List<Term> similarTerms = client.getSimilarTermsForTerm("tiger", -1, PosType.NOUN, 0, 20, true);
        for (Term t : similarTerms) {
            //System.out.println(t.toJson());
        }
        Text t1 = new Text("Incomplete octets - coordinate covalent bond: accepting a pair of e- from another atom to complete its shell");
        Text t2 = new Text("Coordinate covelent bonds accept a pair of electrons from another atom to complete its valence shell");

        ArrayList<Compare.CompareModel> cl = new ArrayList<Compare.CompareModel>();
        cl.add(new Compare.CompareModel(t1,t2));
        Metric[] x = client.compareBulk(cl);


        List<Retina> z = client.getRetinas();
        for (Retina r : z) {
            r.getRetinaName();
        }
        //System.out.println(x[0].getCosineSimilarity());
        //System.out.println(x[0].getEuclideanDistance());

    }

    public static void main (String[] args) throws ApiException, JsonProcessingException {
        PracticeCortical pc = new PracticeCortical();

        pc.testAPI();


    }



}
