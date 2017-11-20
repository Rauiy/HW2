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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Steven on 2017-11-20.
 */
public class ArtistManager extends Agent {

    private List<AID> curators;
    private int maxCurators = 5;
    private int minCurators = 3;
    private AuctionItem item;
    private final double modifier = 500; //0.5
    private final int strategy = 0; // 0 = static, 1 = percentage,
    protected void setup(){
        curators = new ArrayList<AID>();
        item = new AuctionItem(10000, 3000, "MonaLisa");
        registerAtDf();
        MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.REQUEST),MessageTemplate.MatchConversationId("JOIN"));
        addBehaviour(new WaitForCurators(this, mt, System.currentTimeMillis()+60000, null, null));
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

            AID curator = msg.getSender();
            curators.add(curator);

            if(curators.size() < maxCurators)
                reset();
        }

        @Override
        public int onEnd(){
            if(curators.size() >= minCurators){
                System.out.println("Not enough curators found");
                reset();
            }
            else{
                System.out.println("Auction has " + curators.size() + " participants");
                addBehaviour(new AuctionStart());
            }

            return super.onEnd();
        }

    }

    private void sendMsg(Agent agent, ACLMessage msg){
        for(AID a:curators)
            msg.addReceiver(a);
        agent.send(msg);
    }

    private class AuctionStart extends OneShotBehaviour{

        @Override
        public void action() {
            ACLMessage inform = new ACLMessage(ACLMessage.INFORM);
            inform.setOntology("AUCTION");
            inform.setConversationId("START");
            sendMsg(myAgent, inform);

            ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
            cfp.setOntology("AUCTION");
            cfp.setConversationId("ITEM");
            try {
                cfp.setContentObject(item);
            } catch (IOException e) {
                e.printStackTrace();
            }
            sendMsg(myAgent,cfp);
            MessageTemplate currentMT = MessageTemplate.and(MessageTemplate.MatchConversationId("ITEM_REVEAL"),
                    MessageTemplate.and(MessageTemplate.MatchOntology("ITEM"),
                                        MessageTemplate.MatchInReplyTo(cfp.getReplyWith())
                    )
            );
            addBehaviour(new ProposeHandler(myAgent, currentMT, System.currentTimeMillis() + 10000,null,null));
        }
    }

    private class ProposeHandler extends MsgReceiver{
        public ProposeHandler(Agent a, MessageTemplate mt, long deadline, DataStore s, Object msgKey) {
            super(a, mt, deadline, s, msgKey);
        }

        @Override
        protected void handleMessage(ACLMessage propose){
            if(propose == null) {
                lowerThePrice();
                return;
            }

            switch (propose.getPerformative()){
                case ACLMessage.PROPOSE:
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

            reset();
        }
    }

    private void lowerThePrice(){
        switch (strategy){
            case 0:
                item.decreasePrice(modifier);
                break;
            case 1:
                item.decreasePercentage(modifier);
                break;
            default:
                item.decreasePrice(modifier);
                break;
        }

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
            end.setConversationId("END");
            if(item.getBuyer() == null){
                end.setContent("Item was unsold");
            }else{
                end.setContent("Item was sold to: " + item.getBuyer().getName());
            }

            sendMsg(myAgent, end);
        }
    }



}
