import jade.core.AID;
import jade.core.Agent;
import jade.core.GenericCommand;
import jade.core.behaviours.*;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.NotUnderstoodException;
import jade.domain.FIPAAgentManagement.RefuseException;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;
import jade.proto.SimpleAchieveREResponder;
import jade.proto.states.MsgReceiver;
import org.omg.PortableInterceptor.SYSTEM_EXCEPTION;

/**
 * Created by Steven on 2017-11-20.
 */
public class CuratorAgent extends Agent{
    private int myBid = 3000;
    private AuctionItem item;
    private AID artistManager = null;
    private int strategy = 0; // 0 = increase with a set value, 1 = increase incrementally, 2 = increase decremental
    private double modifier = 500; // 1.2; // 1000
    protected void setup() {

        Object[] args = getArguments();
        if(args.length > 0)
            strategy = Integer.parseInt((String)args[0]);
        if(args.length > 1)
            modifier = Double.parseDouble((String)args[1]);

        System.out.println(getLocalName() + ": Starting with strategy: " + strategy + " and modifier: " + modifier);

        // Template for receiving auction begin
        final MessageTemplate informTemplate = MessageTemplate.and(MessageTemplate.MatchConversationId("START"),
                MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                                    MessageTemplate.MatchOntology("AUCTION")));

        // Template for matching all auction messages
        final MessageTemplate auctionTemplate = MessageTemplate.and(MessageTemplate.MatchConversationId("ITEM"),
                MessageTemplate.MatchOntology("AUCTION"));

        while (artistManager == null) {
            artistManager = findAgent(this, "AUCTION");
        }

        SequentialBehaviour sb = new SequentialBehaviour();
        sb.addSubBehaviour(new joinAuction());
        sb.addSubBehaviour(new receiveInfo(this, informTemplate, System.currentTimeMillis()+30000, null, null));
        sb.addSubBehaviour(new receiveProposal(this, auctionTemplate));

        addBehaviour(sb);
        /*
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                SequentialBehaviour sb = new SequentialBehaviour();
                sb.addSubBehaviour(new joinAuction());
                sb.addSubBehaviour(new receiveInfo(myAgent, mt, System.currentTimeMillis()+30000, null, null));
                sb.addSubBehaviour(new receiveProposal(myAgent, mt));

                myAgent.addBehaviour(sb);
            }
        });*/

    }

    private class joinAuction extends OneShotBehaviour{
        @Override
        public void action() {
            System.out.println(getLocalName()+": Joining auction");
            ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
            msg.setConversationId("JOIN");
            msg.addReceiver(artistManager);
            myAgent.send(msg);
        }
    }

    private class receiveInfo extends MsgReceiver {
        receiveInfo(Agent a, MessageTemplate mt, long dl, DataStore ds, Object msgKey) {
            super(a, mt, dl, ds, msgKey);
        }

        @Override
        protected void handleMessage(ACLMessage msg) {
            if(msg == null)
                System.out.println("Never got ready msg");
            else
                System.out.println(myAgent.getLocalName() + " is ready for auction!");
        }
    }

    public class receiveProposal extends SimpleAchieveREResponder {
        boolean done = false;

        public receiveProposal(Agent a, MessageTemplate mt) {
            super(a, mt);
        }

        @Override
        public ACLMessage prepareResponse(ACLMessage request) {
            ACLMessage response = null;
            try{
                switch (request.getPerformative()) {
                    case ACLMessage.CFP:
                        item = (AuctionItem) request.getContentObject();
                        System.out.println(myAgent.getLocalName() + ": item: " + item.getName() + " price: " + item.getCurrentPrice() + " my bid: " + myBid);
                        if (item.getCurrentPrice() <= myBid) {
                            response = request.createReply();
                            response.setContent("test");
                            response.setPerformative(ACLMessage.PROPOSE);
                        }
                        else{
                            //System.out.println(getLocalName() + ": too expensive");
                            increaseBid();
                        }
                        break;
                    case ACLMessage.ACCEPT_PROPOSAL:
                        System.out.println(myAgent.getLocalName() + " has won the " + item.getName());
                        break;
                    case ACLMessage.REJECT_PROPOSAL:
                        System.out.println(myAgent.getLocalName() + " has lost the " + item.getName());
                        // Use strategy change bid
                        increaseBid();
                        break;
                    case ACLMessage.INFORM:
                        done = true;
                        String str = request.getContent();
                        //System.out.println(getLocalName() + " auction ended, reason: " + str);
                        System.out.println(getLocalName() + ": had the strategy: " + strategy + " and modifier: " + modifier);
                        break;
                    default:
                        response = request.createReply();
                        response.setPerformative(ACLMessage.NOT_UNDERSTOOD);
                }

            } catch (UnreadableException e) {
                e.printStackTrace();
            }

            return response;
        }

        @Override
        protected ACLMessage prepareResultNotification(ACLMessage request, ACLMessage response){
            return null;
        }

        @Override
        public boolean done() {
            return false;
        }
    }

    private void increaseBid(){
        switch (strategy){
            case 0:
                // Flat bid increase, always same increase
                myBid += modifier;
                break;
            case 1:
                // Incremented bid increase, begin low end high
                myBid = (int)(myBid*modifier);
                break;
            case 2:
                // Decremented bid increase, begin high end low
                modifier = modifier * 0.9;
                myBid += modifier;
                break;
            default:
                // If nothing is said add a flat modifier
                modifier += 500;
                break;
        }
    }

    static public AID findAgent(Agent myAgent, String type) {
        AID tmp = null;
        DFAgentDescription template = new DFAgentDescription();
        // to find the right service type imm
        ServiceDescription sd = new ServiceDescription();
        sd.setType(type);
        template.addServices(sd);
        try {
            // To get all available services, don't define a template
            DFAgentDescription[] result = DFService.search(myAgent, template);
            // System.out.print("Found the following agents: ");
            // Should only exist one agent of each, so take the first one
            if(result.length > 0){
                tmp = result[0].getName(); // take the first agent with right service available
                System.out.println("Found auctioneer: " + tmp.getLocalName());
            }

        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        return tmp;
    }
}
