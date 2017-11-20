import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.SimpleAchieveREInitiator;
import jade.proto.states.MsgReceiver;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Steven on 2017-11-20.
 */
public class ArtistManager extends Agent {

    private List<AID> curators;
    private int maxCurators = 5;
    private AuctionItem artifact;
    private final int modifier = 500;
    private final int startegy = 0; // 0 = static, 1 = percentage,
    private int round;
    protected void setup(){
        curators = new ArrayList<AID>();
        artifact = new AuctionItem(10000, 3000, "MonaLisa");

    }

    private class WaitForCurators extends MsgReceiver {

        @Override
        protected void handleMessage(ACLMessage msg){
            AID curator = msg.getSender();
            curators.add(curator);
        }

        @Override
        public int onEnd(){
            if(curators.size() != maxCurators)
                reset();
            else{
                round = 0;
                ACLMessage msg = new ACLMessage(ACLMessage.CFP);
                addBehaviour(new InformAuctionBegin());
            }

            return super.onEnd();
        }

    }

    private class InformAuctionBegin extends OneShotBehaviour{
        @Override
        public void action() {
            ACLMessage inform = new ACLMessage(ACLMessage.INFORM);
            inform.setOntology("AUCTION_START");
            for(AID a:curators){
                inform.addReceiver(a);
            }

            myAgent.send(inform);

        }
    }

    private class AuctionStart extends OneShotBehaviour{
        @Override
        public void action() {

        }
    }

    private class ProposeHandler extends MsgReceiver{

        @Override
        protected void handleMessage(ACLMessage propose){

        }
    }




}
