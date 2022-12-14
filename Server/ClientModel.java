import java.nio.channels.SelectionKey;

public class ClientModel implements Comparable{
    String buffer;
    String name;
    String room;
    SelectionKey key;
    public ClientModel() {
        this.buffer = "";
        this.name = "";
        this.room = "";
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

    public String getRoom() {
        return room;
    }

    public void setRoom(String room) {
        this.room = room;
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