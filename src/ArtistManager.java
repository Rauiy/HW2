import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.DataStore;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.SequentialBehaviour;
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
    private double modifier = 0.9; //500, 0.9, 500
    private final int strategy = 1; // 0 = static, 1 = decremented decrease, 2 = incremented decrease
    private Random r = new Random();
    private int rounds = 0;
    protected void setup(){
        curators = new ArrayList<AID>();
        item = new AuctionItem(r.nextInt(20000) +  5000, r.nextInt(3000)+1000, "MonaLisa");
        registerAtDf();
        MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.REQUEST),MessageTemplate.MatchConversationId("JOIN"));
        addBehaviour(new WaitForCurators(this, mt, System.currentTimeMillis()+20000, null, null));
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
            if(msg == null){
                System.out.println("Timeout: we got " + curators.size() + " participants");
                return;
            }
            System.out.println("Curator joined");
            AID curator = msg.getSender();
            curators.add(curator);
        }

        @Override
        public int onEnd(){
            if(curators.size() >= minCurators){
                System.out.println("Auction has " + curators.size() + " participants");
                myAgent.addBehaviour(new InformOfAuctionStart());
            }
            else{
                System.out.println("Not enough curators found");
                reset();
                myAgent.addBehaviour(this);
            }
            return super.onEnd();
        }

    }

    private void sendMsg(Agent agent, ACLMessage msg){
        for(AID a:curators)
            msg.addReceiver(a);
        agent.send(msg);
    }

    private class InformOfAuctionStart extends OneShotBehaviour{

        @Override
        public void action() {
            System.out.println(getLocalName() + ": Initiating auction");
            ACLMessage inform = new ACLMessage(ACLMessage.INFORM);
            inform.setOntology("AUCTION");
            inform.setConversationId("START");
            sendMsg(myAgent, inform);

            myAgent.addBehaviour(new AuctionStart());
        }
    }

    private class AuctionStart extends OneShotBehaviour{

        @Override
        public void action() {
            rounds++;
            System.out.println(getLocalName() + ": sending CFP");
            ACLMessage msg = new ACLMessage(ACLMessage.CFP);
            msg.setOntology("AUCTION");
            msg.setConversationId("ITEM");

            try {
                msg.setContentObject(item);
            } catch (IOException e) {
                e.printStackTrace();
            }
            sendMsg(myAgent,msg);

            MessageTemplate currentMT = MessageTemplate.and(MessageTemplate.MatchConversationId("ITEM"),
                    MessageTemplate.MatchOntology("AUCTION"));
            addBehaviour(new ProposeHandler(myAgent, currentMT, System.currentTimeMillis() + 5000,null,null));
        }
    }

    private class ProposeHandler extends MsgReceiver{
        public ProposeHandler(Agent a, MessageTemplate mt, long deadline, DataStore s, Object msgKey) {
            super(a, mt, deadline, s, msgKey);
        }
        boolean res = false;
        @Override
        protected void handleMessage(ACLMessage propose){
            if(propose == null) {
                lowerThePrice();
                return;
            }

            res = true;

            switch (propose.getPerformative()){
                case ACLMessage.PROPOSE:
                    System.out.println(getLocalName() + ": Got proposal");
                    if(!item.isSold()){
                        ACLMessage res = propose.createReply();
                        res.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                        myAgent.send(res);
                        item.setBuyer(propose.getSender());
                    }
                    else{ // The item has already been sold
                        ACLMessage res = propose.createReply();
                        res.setPerformative(ACLMessage.REJECT_PROPOSAL);
                        myAgent.send(res);
                    }
                    break;
                // case ACLMessage.NOT_UNDERSTOOD: // implied
                default:
                        break;
            }
        }

        @Override
        public int onEnd(){
            if(res && !item.isSold()) {
                reset();
                myAgent.addBehaviour(this);
            }
            else if(item.isSold())
                myAgent.addBehaviour(new endAuction());

            return super.onEnd();
        }
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

        System.out.println(getLocalName() + ": Price to high, lowering it. New Price: " + item.getCurrentPrice());

        if(item.getCurrentPrice() >= item.getLimit())
            addBehaviour(new AuctionStart());
        else
            addBehaviour(new endAuction());
    }

    private class endAuction extends OneShotBehaviour{
        @Override
        public void action() {
            ACLMessage end = new ACLMessage(ACLMessage.INFORM);
            end.setOntology("AUCTION");
            end.setConversationId("ITEM");
            String str = "Nothing";
            if(item.getBuyer() == null){
                str = getLocalName() + ": Item was unsold";
            }else{
                str = getLocalName() + ": Item was sold to: " + item.getBuyer().getLocalName();
            }

            end.setContent(str);
            System.out.println(str + " in " + rounds + " rounds");

            sendMsg(myAgent, end);
        }
    }



}
