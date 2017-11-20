import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.SimpleAchieveREResponder;
import jade.proto.states.MsgReceiver;

/**
 * Created by Steven on 2017-11-20.
 */
public class CuratorAgent extends Agent{
    private AID artistManager = null;
    private int myBid;

    protected void setup() {

        final MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchConversationId("Artist-manager"),
                MessageTemplate.MatchPerformative(ACLMessage.CFP));

        while(artistManager == null) {
            artistManager = findArtist(this);
        }
        addBehaviour(new CyclicBehaviour() {

            @Override
            public void action() {
                SequentialBehaviour sb = new SequentialBehaviour();

                sb.addSubBehaviour(new receiveInfo());
                sb.addSubBehaviour(new receiveProposal(myAgent, mt));
            }
        });

    }

    public class receiveInfo extends MsgReceiver {
        @Override
        protected void handleMessage(ACLMessage msg) {
            super.handleMessage(msg);
            System.out.println(myAgent + " is ready for auction");
        }
    }

    public class receiveProposal extends SimpleAchieveREResponder {

        public receiveProposal(Agent a, MessageTemplate mt) {
            super(a, mt);
        }
    }

    static private AID findArtist(Agent myAgent) {
        AID tmp = null;
        DFAgentDescription template = new DFAgentDescription();
        // to find the right service type imm
        ServiceDescription sd = new ServiceDescription();
        sd.setType("Artist-Manager");
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
