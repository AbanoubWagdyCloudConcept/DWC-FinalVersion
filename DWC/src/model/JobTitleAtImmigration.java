package model;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

//import org.codehaus.jackson.annotate.JsonProperty;

/**
 * Created by Abanoub on 6/25/2015.
 */

/**
 * This class holds JobTitleAtImmigration attributes
 */
public class JobTitleAtImmigration implements Serializable {

//    public model.attributes getAttributes() {
//        return attributes;
//    }
//
//    public void setAttributes(model.attributes attributes) {
//        this.attributes = attributes;
//    }
//
//    @JsonProperty("attributes")
//    attributes attributes;

    //    @JsonProperty("Id")
    @SerializedName("Id")
    public String ID;
    //    @JsonProperty("Name")
    @SerializedName("Name")
    public String Name;


    public String getID() {
        return ID;
    }

    public void setID(String ID) {
        this.ID = ID;
    }

    public String getName() {
        return Name;
    }

    public void setName(String name) {
        Name = name;
    }
}
