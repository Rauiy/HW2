import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.*;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.SimpleAchieveREInitiator;
import jade.proto.states.MsgReceiver;
import sun.plugin2.message.Message;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Vector;

/**
 * Created by Steven on 2017-11-20.
 */
public class ArtistManager extends Agent {

    private List<AID> curators;
    private int maxCurators = 5;
    private int minCurators = 3;
    private AuctionItem item;
    private double modifier = 750; //500, 0.9, 500
    private final int strategy = 0; // 0 = flat decrease, 1 = decremented decrease, 2 = incremented decrease
    private Random r = new Random();
    private int rounds = 0;
    protected void setup(){
        curators = new ArrayList<AID>();
        registerAtDf();


        MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM),MessageTemplate.MatchConversationId("JOIN"));
        addBehaviour(new WaitForCurators(this,mt,System.currentTimeMillis() + 5000, null, null));
    }


    public void registerAtDf(){
        // Register the tour guide service in the yellow pages
        DFAgentDescription template = new DFAgentDescription();
        template.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("AUCTION");
        sd.setName(getLocalName());
        template.addServices(sd);
        try {
            DFService.register(this, template);
        }
        catch (FIPAException fe) {
            fe.printStackTrace();
        }
    }

    private class WaitForCurators extends MsgReceiver {
        public WaitForCurators(Agent a, MessageTemplate mt, long deadline, DataStore s, Object msgKey) {
            super(a, mt, deadline, s, msgKey);
        }

        @Override
        protected void handleMessage(ACLMessage msg){
            if(msg != null) {
                //System.out.println("Curator joined");
                AID curator = msg.getSender();
                curators.add(curator);
            }
        }

        @Override
        public int onEnd() {
            if(curators.size() >= minCurators)
                startAuction(myAgent);
            else{
                reset();
                addBehaviour(this);
            }
            return super.onEnd();
        }

    }

    private void startAuction(Agent myAgent){
        SequentialBehaviour sb = new SequentialBehaviour();
        sb.addSubBehaviour(new InformOfAuctionStart());
        sb.addSubBehaviour(new AuctionCycles());

        myAgent.addBehaviour(sb);
    }

    private void sendMsg(Agent agent, ACLMessage msg){
        for(AID a:curators)
            msg.addReceiver(a);
        agent.send(msg);
    }

    private void lowerThePrice(){
        switch (strategy){
            case 0:
                // Flat price decrease
                item.decreasePrice(modifier);
                break;
            case 1:
                // Decremented price decrease
                item.decreasePercentage(modifier);
                break;
            case 2:
                // Incremented price decrease
                modifier = modifier * 1.1;
                item.decreasePrice(modifier);
                break;
            default:
                item.decreasePrice(modifier);
                break;
        }
        //System.out.println(getLocalName() + ": Price to high, lowering it. New Price: " + item.getCurrentPrice());
    }

    private class InformOfAuctionStart extends OneShotBehaviour{

        @Override
        public void action() {
            //System.out.println(getLocalName() + ": Initiating auction");
            ACLMessage inform = new ACLMessage(ACLMessage.INFORM);
            inform.setOntology("AUCTION");
            inform.setConversationId("START");
            sendMsg(myAgent, inform);
            item = new AuctionItem(r.nextInt(10000) +  5000, r.nextInt(3000)+1000, "MonaLisa" + r.nextInt(10000));
            System.out.println("New auction: " + item.toString());
        }
    }

    private class AuctionCycles extends Behaviour {
        private int state = 0;
        private int rounds = 0;
        private MessageTemplate mt;
        private boolean done = false;
        @Override
        public void action() {
            if(item.getCurrentPrice() < item.getLimit())
                state = 2;

            switch (state){
                case 0:
                    //System.out.println(getLocalName() + ": Starting auction for: " + item.getName());
                    rounds++;
                    ACLMessage msg = new ACLMessage(ACLMessage.CFP);
                    msg.setOntology("AUCTION");
                    msg.setConversationId("ITEM");

                    try {
                        msg.setContentObject(item);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    sendMsg(myAgent,msg);

                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId("ITEM"),
                            MessageTemplate.MatchOntology("AUCTION"));
                    state = 1;
                    break;
                case 1:
                    ACLMessage proposals = blockingReceive(mt, 5000);
                    if(proposals == null){
                        lowerThePrice();
                        state = 0;
                        break;
                    }

                    switch (proposals.getPerformative()){
                        case ACLMessage.PROPOSE:
                            //System.out.println(getLocalName() + ": Got proposal");
                            if(!item.isSold()){
                                ACLMessage res = proposals.createReply();
                                res.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                                myAgent.send(res);
                                item.setBuyer(proposals.getSender());
                            }
                            else{ // The item has already been sold
                                ACLMessage res = proposals.createReply();
                                res.setPerformative(ACLMessage.REJECT_PROPOSAL);
                                myAgent.send(res);
                            }
                            break;
                        case ACLMessage.NOT_UNDERSTOOD: // implied in default
                        default:
                            break;
                    }

                    state = 2;
                    break;
                case 2:
                    ACLMessage end = new ACLMessage(ACLMessage.INFORM);
                    end.setOntology("AUCTION");
                    end.setConversationId("ITEM");
                    String str;
                    if(item.isSold()){
                        str = getLocalName() + ": Item was sold to: " + item.getBuyer().getLocalName();
                    }else{
                        str = getLocalName() + ": Item was unsold, due to low bids or not understood";
                    }

                    end.setContent(str);
                    System.out.println(str + " in " + rounds + " rounds");
                    sendMsg(myAgent, end);
                default:
                    done = true;
                    break;
            }
        }

        @Override
        public boolean done() {
            if(done)
                startAuction(myAgent);
            return done;
        }
    }
}
