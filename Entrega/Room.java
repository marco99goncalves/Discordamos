import java.util.TreeSet;

public class Room implements Comparable<Room> {

  TreeSet<ClientModel> clients;
  String roomName;

  public Room(String roomName) {
    this.roomName = roomName;
    clients = new TreeSet<>();
  }

  @Override
  public String toString() {
    return roomName;
  }

  public String getRoomName() {
    return roomName;
  }

  public void setRoomName(String roomName) {
    this.roomName = roomName;
  }

  @Override
  public int compareTo(Room room) {
    return this.roomName.compareTo(room.roomName);
  }
}
