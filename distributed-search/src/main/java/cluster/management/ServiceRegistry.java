package cluster.management;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ServiceRegistry implements Watcher {

    public static final String WORKERS_REGISTRY_ZNODE = "/workers_service_registry";
    public static final String COORDINATORS_REGISTRY_ZNODE = "/coordinators_service_registry";
    private final ZooKeeper zooKeeper;
    private String currentZNode = null;
    private List<String> allServiceAddresses = null;
    private final String serviceRegistryZNode;

    public ServiceRegistry(ZooKeeper zooKeeper, String serviceRegistryZNode) {
        this.zooKeeper = zooKeeper;
        this.serviceRegistryZNode = serviceRegistryZNode;
        createServiceRegistryZNode();
    }

    // 生成 registry Z node, 监听其他node的变化
    private void createServiceRegistryZNode() {
        try {
            if (zooKeeper.exists(serviceRegistryZNode, false) == null) {
                zooKeeper.create(serviceRegistryZNode, new byte[]{}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            }
        } catch (KeeperException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void registerToCluster(String metadata) throws InterruptedException, KeeperException {
        if (this.currentZNode != null) {
            System.out.println("Already registered to service registry");
            return;
        }
        this.currentZNode = zooKeeper.create(serviceRegistryZNode + "/n_",
                metadata.getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
        System.out.println("Registered to service registry");
    }

    public void registerForUpdates() {
        try {
            updateAddresses();
        } catch (InterruptedException | KeeperException ignored) {
        }
    }

    public synchronized List<String> getAllServiceAddresses() throws InterruptedException, KeeperException {
        if (allServiceAddresses == null) updateAddresses();
        return allServiceAddresses;
    }

    public void unregisterFromCluster() {
        try {
            if (currentZNode != null && zooKeeper.exists(currentZNode, false) != null) {
                zooKeeper.delete(currentZNode, -1);
            }
        } catch (KeeperException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private synchronized void updateAddresses() throws InterruptedException, KeeperException {
        List<String> workerZNodes = zooKeeper.getChildren(serviceRegistryZNode, this);

        List<String> addresses = new ArrayList<>(workerZNodes.size());

        for (String workerZNode: workerZNodes) {
            String workerZNodeFullPath = serviceRegistryZNode + "/" + workerZNode;
            Stat stat = zooKeeper.exists(workerZNodeFullPath, false);
            if (stat == null) continue;
            byte[] addressBytes = zooKeeper.getData(workerZNodeFullPath, false, stat);
            addresses.add(new String(addressBytes));
        }

        this.allServiceAddresses = Collections.unmodifiableList(addresses);
        System.out.println("The cluster addresses are: " + this.allServiceAddresses);
    }

    @Override
    public void process(WatchedEvent watchedEvent) {
        try {
            updateAddresses();
        } catch (InterruptedException | KeeperException ignored) {
        }
    }
}
