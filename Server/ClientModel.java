public class ClientModel {
    String buffer;
    String name;
    String room;

    public ClientModel() {
        this.buffer = "";
        this.name = "";
        this.room = "";
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

}
