public class ServerResponse {
    String status;
    String message;

    public ServerResponse(String status_message){
        if (status_message == null || status_message.isEmpty())
            return;
        String[] str = status_message.split("-");
        this.status = str[0];
        this.message = str[1];
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return message;
    }
}