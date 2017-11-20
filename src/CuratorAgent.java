import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.DataStore;
import jade.core.behaviours.SequentialBehaviour;
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

/**
 * Created by Steven on 2017-11-20.
 */
public class CuratorAgent extends Agent{
    private int myBid;
    private AuctionItem item;
    private AID artifactManager = null;
    protected void setup() {

        final MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchConversationId("Artist-manager"),
                MessageTemplate.MatchPerformative(ACLMessage.CFP));

        while (artifactManager == null) {
            artifactManager = findAgent(this, "Artifact-manager");
        }

        addBehaviour(new CyclicBehaviour() {

            @Override
            public void action() {


                SequentialBehaviour sb = new SequentialBehaviour();

                sb.addSubBehaviour(new receiveInfo(myAgent, mt, Long.MAX_VALUE, null, null));
                sb.addSubBehaviour(new receiveProposal(myAgent, mt));
            }
        });

    }

    public class receiveInfo extends MsgReceiver {
        receiveInfo(Agent a, MessageTemplate mt, long dl, DataStore ds, Object msgKey) {
            super(a, mt, dl, ds, msgKey);
        }
        @Override
        protected void handleMessage(ACLMessage msg) {

            System.out.println(myAgent.getLocalName() + " is ready for auction!");
            super.handleMessage(msg);
        }
    }

    public class receiveProposal extends SimpleAchieveREResponder {

        receiveProposal(Agent a, MessageTemplate mt) {
            super(a, mt);
        }

        @Override
        protected ACLMessage prepareResponse(ACLMessage request) throws NotUnderstoodException, RefuseException {

            try {
                ACLMessage reply = request.createReply();

                switch (request.getPerformative()) {
                    case ACLMessage.CFP:
                        item = (AuctionItem) request.getContentObject();
                        if (item.getCurrentPrice() <= myBid) {
                            System.out.println(myAgent.getLocalName() + " proposes to buy " + item.getName());
                            reply.setPerformative(ACLMessage.PROPOSE);
                        }
                        break;
                    case ACLMessage.ACCEPT_PROPOSAL:
                        System.out.println(myAgent.getLocalName() + " has won the " + item.getName());
                        break;
                    case ACLMessage.REJECT_PROPOSAL:
                        System.out.println(myAgent.getLocalName() + " has lost the " + item.getName());
                        break;
                    case ACLMessage.INFORM:

                        break;
                    default:
                        reply.setPerformative(ACLMessage.NOT_UNDERSTOOD);
                }
                    return reply;

            } catch (UnreadableException e) {
                e.printStackTrace();
            }
            return super.prepareResponse(request);
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
            }

        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        return tmp;
    }
}
