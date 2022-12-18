import java.nio.channels.SelectionKey;

public class ClientModel implements Comparable{
    String buffer;
    String name;
    Room room;
    SelectionKey key;
    public ClientModel() {
        this.buffer = "";
        this.name = "";
        this.room = null;
        this.key = null;
    }

    public String getBuffer() {
        return buffer;
    }

    public void setBuffer(String buffer) {
        this.buffer = buffer;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Room getRoom() {
        return room;
    }

    public void setRoom(Room room) {
        if(this.room != null)
            this.room.clients.remove(this);

        this.room = room;
        if(room != null){
            room.clients.add(this);
        }
    }

    public SelectionKey getKey() {
        return key;
    }

    public void setKey(SelectionKey key) {
        this.key = key;
    }

    @Override
    public int compareTo(Object other) {
        return (this.name.compareTo(((ClientModel)other).name));
    }
}