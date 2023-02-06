package cluster.management;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.util.Collections;
import java.util.List;

public class LeaderElection implements Watcher {

    private static final String ELECTION_NAMESPACE = "/election";
    private String currentZNodeName;
    private final ZooKeeper zooKeeper;
    private final OnElectionCallback onElectionCallback;

    public LeaderElection(ZooKeeper zooKeeper, OnElectionCallback onElectionCallback) {
        this.zooKeeper = zooKeeper;
        this.onElectionCallback = onElectionCallback;
    }

    public void volunteerForLeadership() throws InterruptedException, KeeperException {
        String zNodePrefix = ELECTION_NAMESPACE + "/c_";
        String zNodeFullPath = zooKeeper.create(zNodePrefix, new byte[]{}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
        System.out.println("Z-node name: " + zNodeFullPath);
        this.currentZNodeName = zNodeFullPath.replace("/election/", "");
    }

    public void reElectLeader() throws InterruptedException, KeeperException {
        String predecessorZNodeName = "";
        Stat predecessorStat = null;

        while (predecessorStat == null) {
            List<String> children = zooKeeper.getChildren(ELECTION_NAMESPACE, false);

            Collections.sort(children);
            // 最小的就是leader, 序列化的第一个加进来的即为leader
            String smallestChild = children.get(0);
            if (smallestChild.equals(currentZNodeName)) {
                System.out.println("I am the leader");
                onElectionCallback.onElectedToBeLeader();
                return;
            } else {
                // make it be able to recover from failure
                System.out.println("I am not the leader");
                // predecessor 是动态调整的, 从中间断开也可以
                int predecessorIndex = Collections.binarySearch(children, currentZNodeName) - 1;
                predecessorZNodeName = children.get(predecessorIndex);
                // 看的是前一个Z node
                predecessorStat = zooKeeper.exists(ELECTION_NAMESPACE + "/" + predecessorZNodeName, this);
            }
        }
        // 不是leader就是worker
        onElectionCallback.onWorker();
        System.out.println("Watching Z node: " + predecessorZNodeName);
        System.out.println();
    }

    @Override
    public void process(WatchedEvent watchedEvent) {
        if (watchedEvent.getType() == Event.EventType.NodeDeleted) {
            try {
                reElectLeader();
            } catch (KeeperException | InterruptedException ignored) {
            }
        }
    }
}
