package fragment;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import com.android.volley.NetworkResponse;
import com.android.volley.VolleyError;
import com.cocosw.bottomsheet.BottomSheet;
import com.google.gson.Gson;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;

import RestAPI.JSONConstants;
import RestAPI.RelatedServiceType;
import RestAPI.RestMessages;
import RestAPI.SFResponseManager;
import RestAPI.SoqlStatements;
import adapter.AttachmentAdapter;
import cloudconcept.dwc.CompanyDocumentsActivity;
import cloudconcept.dwc.R;
import utilities.StoreData;
import fragmentActivity.BaseFragmentActivity;
import fragmentActivity.CardActivity;
import fragmentActivity.CompanyNocActivity;
import fragmentActivity.NocActivity;
import fragmentActivity.VisaActivity;
import model.Company_Documents__c;
import model.Receipt_Template__c;
import model.User;
import utilities.AdapterConfiguration;
import utilities.Utilities;

/**
 * Created by M_Ghareeb on 10/20/2015.
 */
public class GenericAttachmentPage extends Fragment {
    private static ListView lstAttachments;
    private RestRequest restRequest;
    static ArrayList<Company_Documents__c> _companyDocuments;
    boolean filesUploaded = true;
    Gson gson;
    private Intent intent;
    private static AttachmentAdapter adapter;
    int i = 0;
    static BaseFragmentActivity activity;

    public static Fragment newInstance(String third) {
        GenericAttachmentPage fragment = new GenericAttachmentPage();
        Bundle bundle = new Bundle();
        bundle.putString("text", third);
        fragment.setArguments(bundle);
        return fragment;
    }


    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.noc_attachment_page, container, false);
        InitializeViews(view);
        activity = (BaseFragmentActivity) getActivity();
        if (activity.geteServiceAdministration() != null) {
            if (!(activity.geteServiceAdministration().getNo_of_Upload_Docs__c() > 0)) {
                PerfromParentNext();
            } else {
                _companyDocuments = new ArrayList<Company_Documents__c>();
                PerformCompanyDocumentsRequest(activity.getInsertedServiceId());
            }
        }else{
            PerfromParentNext();
        }

        return view;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (activity.geteServiceAdministration() != null) {
            if (!(activity.geteServiceAdministration().getNo_of_Upload_Docs__c() > 0)) {
                PerfromParentNext();
            }
        } else {
            PerfromParentNext();
        }
    }

    private void PerformCompanyDocumentsRequest(String nocRecordTypeId) {

        final String soql = SoqlStatements.getInstance().constructCompanyDocumentsQuery(nocRecordTypeId);
        try {
            restRequest = RestRequest.getRequestForQuery(getString(R.string.api_version), soql);
            new ClientManager(getActivity(), SalesforceSDKManager.getInstance().getAccountType(), SalesforceSDKManager.getInstance().getLoginOptions(), SalesforceSDKManager.getInstance().shouldLogoutWhenTokenRevoked()).getRestClient(getActivity(), new ClientManager.RestClientCallback() {
                @Override
                public void authenticatedRestClient(final RestClient client) {
                    if (client == null) {
                        SalesforceSDKManager.getInstance().logout(getActivity());
                        return;
                    } else {
                        Utilities.showloadingDialog(getActivity());
                        client.sendAsync(restRequest, new RestClient.AsyncRequestCallback() {

                            @Override
                            public void onSuccess(RestRequest request, RestResponse result) {
                                _companyDocuments = SFResponseManager.parseCompanyDocumentObjectWithGson(result.toString());
                                if (_companyDocuments != null && _companyDocuments.size() > 0) {
                                    for (Company_Documents__c item : _companyDocuments) {
                                        if (item.getAttachment_Id__c() == null)
                                            item.setHasAttachmentBefore(false);
                                        else
                                            item.setHasAttachmentBefore(true);
                                    }
                                    lstAttachments.setAdapter(new AttachmentAdapter(getActivity().getApplicationContext(), _companyDocuments));
                                    if (activity instanceof CardActivity & activity.getType().equals("3"))
                                        attachOldToNew(((CardActivity) activity));
                                } else {
                                    filesUploaded = false;
                                    String soqlCompanyRecordType = SoqlStatements.getInstance().constructCompanyDocumentsRecordType();
                                    PerformCompanyDocumentsRecordTypeRequest(client, soqlCompanyRecordType);
                                }
                            }

                            @Override
                            public void onError(Exception exception) {
                                VolleyError volleyError = (VolleyError) exception;
                                NetworkResponse response = volleyError.networkResponse;
                                String json = new String(response.data);
                                Log.d("", json);
                                Utilities.dismissLoadingDialog();
                                getActivity().finish();
                            }
                        });
                    }
                }
            });
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    private void PerformCompanyDocumentsRecordTypeRequest(final RestClient client, final String soql) {
        if (client == null) {
            return;
        } else {
            try {
                final RestRequest restRequestCompanyDocumentsRecordType = RestRequest.getRequestForQuery(getString(R.string.api_version), soql);
                client.sendAsync(restRequestCompanyDocumentsRecordType, new RestClient.AsyncRequestCallback() {

                    @Override
                    public void onSuccess(RestRequest request, RestResponse result) {
                        String recordId = SFResponseManager.parseCompanyDocumentRecordType(result.toString());
                        PerformEServiceDocumentChecklist(client, recordId);
                    }

                    @Override
                    public void onError(Exception exception) {
                        Utilities.showToast(getActivity(), RestMessages.getInstance().getErrorMessage());
                        Utilities.dismissLoadingDialog();
                        getActivity().finish();
                    }
                });
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
    }

    private void PerformEServiceDocumentChecklist(final RestClient client, final String recordId) {
        if (client == null) {
            return;
        }
        Receipt_Template__c receipt_template__c = activity.geteServiceAdministration();
        final String soql = "select Id , Required_Scan_copy__c , name , Document_Type__c , Instructions__c from eServices_Document_Checklist__c where eService_Administration__c = " + "\'" + receipt_template__c.getID() + "\'";
        try {
            final RestRequest restRequestCompanyDocumentsRecordType = RestRequest.getRequestForQuery(getString(R.string.api_version), soql);
            client.sendAsync(restRequestCompanyDocumentsRecordType, new RestClient.AsyncRequestCallback() {

                @Override
                public void onSuccess(RestRequest request, RestResponse result) {

                    Log.d("", result.toString());
                    Gson gson = new Gson();
                    User user = gson.fromJson(new StoreData(getActivity().getApplicationContext()).getUserDataAsString(), User.class);
                    _companyDocuments = SFResponseManager.parseCompanyDocumentChecklist(activity.getInsertedCaseId(), user.get_contact().get_account().getID(), recordId, activity.getUser().get_contact().get_account().get_currentLicenseNumber().getId(), result.toString());
                    for (int i = 0; i < _companyDocuments.size(); i++) {
                        _companyDocuments.get(i).setHasAttachment(false);
                        if (_companyDocuments.get(i).getAttachment_Id__c() == null) {
                            _companyDocuments.get(i).setHasAttachmentBefore(false);
                        } else {
                            _companyDocuments.get(i).setHasAttachmentBefore(true);
                        }
                    }

                    if (_companyDocuments.size() > 0) {
                        CreateCompanyDocuments(client, _companyDocuments);
                    } else {
                        PerfromParentNext();
                    }
                }

                @Override
                public void onError(Exception exception) {
                    VolleyError volleyError = (VolleyError) exception;
                    NetworkResponse response = volleyError.networkResponse;
                    String json = new String(response.data);
                    Log.d("", json);
                    Utilities.dismissLoadingDialog();
                    getActivity().finish();
                }
            });
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }


    private void CreateCompanyDocuments(final RestClient client, final ArrayList<Company_Documents__c> companyDocuments) {

        Gson gson = new Gson();
        User user = gson.fromJson(new StoreData(getActivity().getApplicationContext()).getUserDataAsString(), User.class);
        final int size = companyDocuments.size();
        if (i < size) {
            Company_Documents__c company_documents__c = companyDocuments.get(i);
            HashMap<String, Object> fields = new HashMap<String, Object>();
            fields.put("Name", company_documents__c.getName());
            fields.put("eServices_Document__c", company_documents__c.getId());
            fields.put("Company__c", user.get_contact().get_account().getID());
            fields.put("Request__c", activity.getInsertedCaseId());

            if (company_documents__c.getHasAttachmentBefore()) {
                try {
                    restRequest = RestRequest.getRequestForUpdate(getActivity().getString(R.string.api_version), "Company_Documents__c", company_documents__c.getId(), fields);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                try {
                    restRequest = RestRequest.getRequestForCreate(getActivity().getString(R.string.api_version), "Company_Documents__c", fields);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            client.sendAsync(restRequest, new RestClient.AsyncRequestCallback() {
                @Override
                public void onSuccess(RestRequest request, RestResponse response) {

                    try {
                        JSONObject jsonObject = new JSONObject(response.toString());
                        companyDocuments.get(i).setId(jsonObject.getString("id"));
                        i++;
                        CreateCompanyDocuments(client, companyDocuments);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onError(Exception exception) {
                    VolleyError volleyError = (VolleyError) exception;
                    NetworkResponse response = volleyError.networkResponse;
                    String json = new String(response.data);
                    Log.d("", json);
                    Utilities.dismissLoadingDialog();
                    getActivity().finish();
                }
            });
        } else {
            if (activity instanceof CardActivity & activity.getType().equals("3"))
                attachOldToNew((CardActivity) activity);
            else {
                lstAttachments.setAdapter(new AttachmentAdapter(getActivity().getApplicationContext(), companyDocuments));
                lstAttachments.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {
                        new BottomSheet.Builder(getActivity()).title("Choose Attachment Option").sheet(R.menu.list).listener(new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                switch (which) {
                                    case R.id.camera:
                                        loadImagefromCamera(position, _companyDocuments.get(position));
                                        break;
                                    case R.id.existing_document:
                                        intent = new Intent(getActivity().getApplicationContext(), CompanyDocumentsActivity.class);
                                        Gson gson = new Gson();
                                        String companyDocumentString = gson.toJson(_companyDocuments.get(position));
                                        intent.putExtra("position", position);
                                        intent.putExtra("company_documents__c", companyDocumentString);
                                        getActivity().startActivityForResult(intent, 1);
                                        break;
                                    case R.id.gallery:
                                        loadImagefromGallery(position, _companyDocuments.get(position));
                                        break;
                                }
                            }
                        }).show();
                    }
                });
            }
            Utilities.dismissLoadingDialog();
        }
    }

    public void loadImagefromGallery(int position, Company_Documents__c company_documents__c) {
        Intent galleryIntent = new Intent(Intent.ACTION_PICK,
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        gson = new Gson();
        new StoreData(getActivity().getApplicationContext()).setCompanyDocumentObject(gson.toJson(company_documents__c));
        new StoreData(getActivity().getApplicationContext()).setCompanyDocumentPosition(position);
        getActivity().startActivityForResult(galleryIntent, AdapterConfiguration.RESULT_LOAD_IMG_FROM_GALLERY);
    }

    public void loadImagefromCamera(int position, Company_Documents__c company_documents__c) {
        Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        gson = new Gson();
        new StoreData(getActivity().getApplicationContext()).setCompanyDocumentObject(gson.toJson(company_documents__c));
        new StoreData(getActivity().getApplicationContext()).setCompanyDocumentPosition(position);
        getActivity().startActivityForResult(cameraIntent, AdapterConfiguration.RESULT_LOAD_IMG_FROM_CAMERA);
    }

    private void PerfromParentNext() {

        if (getParentFragment() instanceof BaseFragmentFiveSteps) {
            Fragment fragment = null;
            if (activity instanceof CardActivity) {
                if (((CardActivity) activity).getType().equals("1")) {
                    fragment = GenericPayAndSubmitFragment.newInstance(RelatedServiceType.RelatedServiceTypeNewCard, ((CardActivity) getActivity()).getCard().getFull_Name__c(), ((CardActivity) getActivity()).getCaseNumber(), Utilities.getCurrentDate(), null, String.valueOf(((CardActivity) getActivity()).geteServiceAdministration().getTotal_Amount__c()), ((CardActivity) getActivity()).get_webForm().get_formFields(), ((CardActivity) getActivity()).getCard(), null);
                } else if (((CardActivity) activity).getType().equals("3")) {
                    fragment = GenericPayAndSubmitFragment.newInstance(RelatedServiceType.RelatedServiceTypeRenewCard, ((CardActivity) getActivity()).getCard().getFull_Name__c(), ((CardActivity) getActivity()).getCaseNumber(), Utilities.getCurrentDate(), null, String.valueOf(((CardActivity) getActivity()).geteServiceAdministration().getTotal_Amount__c()), ((CardActivity) getActivity()).get_webForm().get_formFields(), ((CardActivity) getActivity()).getCard(), null);
                } else if (((CardActivity) activity).getType().equals("2")) {
                    fragment = GenericPayAndSubmitFragment.newInstance(RelatedServiceType.RelatedServiceTypeCancelCard, ((CardActivity) getActivity()).getCard().getFull_Name__c(), ((CardActivity) getActivity()).getCaseNumber(), Utilities.getCurrentDate(), null, String.valueOf(((CardActivity) getActivity()).geteServiceAdministration().getTotal_Amount__c()), ((CardActivity) getActivity()).get_webForm().get_formFields(), ((CardActivity) getActivity()).getCard(), null);
                } else if (((CardActivity) activity).getType().equals("4")) {
                    fragment = GenericPayAndSubmitFragment.newInstance(RelatedServiceType.RelatedServiceTypeReplaceCard, ((CardActivity) getActivity()).getCard().getFull_Name__c(), ((CardActivity) getActivity()).getCaseNumber(), Utilities.getCurrentDate(), null, String.valueOf(((CardActivity) getActivity()).geteServiceAdministration().getTotal_Amount__c()), ((CardActivity) getActivity()).get_webForm().get_formFields(), ((CardActivity) getActivity()).getCard(), null);
                }
            }
            ((BaseFragmentFiveSteps) getParentFragment()).gotoPayAndSubmitFragment(fragment);
        } else if (getParentFragment() instanceof BaseFragmentFourStepsNew) {
            if (activity instanceof VisaActivity) {
                ((BaseFragmentFourStepsNew) getParentFragment()).gotoPayAndSubmitFragment(GenericPayAndSubmitFragment.newInstance(RelatedServiceType.RelatedServiceTypeCancelVisa, ((VisaActivity) getActivity()).getVisa().getName(), ((VisaActivity) getActivity()).getCaseNumber(), Utilities.getCurrentDate(), "Draft", Utilities.stringNotNull(String.valueOf(((VisaActivity) getActivity()).geteServiceAdministration().getTotal_Amount__c())), null, null, null));
            }
        }

        if (getParentFragment() instanceof BaseServiceFragment && activity instanceof CompanyNocActivity)
            ((BaseServiceFragment) getParentFragment()).AddNextFragment("Com");
        if (getParentFragment() instanceof BaseServiceFragment && activity instanceof NocActivity)
            ((BaseServiceFragment) getParentFragment()).AddNextFragment("Emp");
    }

    private void InitializeViews(View view) {
        lstAttachments = (ListView) view.findViewById(R.id.lstAttachments);
        _companyDocuments = new ArrayList<Company_Documents__c>();
        gson = new Gson();
    }

    public static void setReturnedCompnayDocument(Company_Documents__c company_documents__c, int position) {
        if (_companyDocuments != null && position != -1) {
            _companyDocuments.set(position, company_documents__c);
        }
        activity.setCompanyDocuments(_companyDocuments);
        adapter = new AttachmentAdapter(activity.getApplicationContext(), _companyDocuments);
        lstAttachments.setAdapter(adapter);
        adapter.notifyDataSetChanged();
    }

    public static boolean ValidateAttachments() {

        for (int i = 0; i < _companyDocuments.size(); i++) {
            if (_companyDocuments.get(i).getAttachment_Id__c() == null || _companyDocuments.get(i).getAttachment_Id__c().equals("")) {
                return false;
            }
        }
        return true;
    }


    public void attachOldToNew(final CardActivity activity) {
        new ClientManager(getActivity(), SalesforceSDKManager.getInstance().getAccountType(), SalesforceSDKManager.getInstance().getLoginOptions(), SalesforceSDKManager.getInstance().shouldLogoutWhenTokenRevoked()).getRestClient(getActivity(), new ClientManager.RestClientCallback() {
            @Override
            public void authenticatedRestClient(final RestClient client) {
                if (client == null) {
                    SalesforceSDKManager.getInstance().logout(getActivity());
                    return;
                } else {
                    final String soql = "select Attachment_Id__c,name,Id from Company_Documents__c where Card_Management__c= '" + activity.getCard().getId() + "'";
                    try {
                        RestRequest restRequestCompanyDocumentsRecordType = RestRequest.getRequestForQuery(getString(R.string.api_version), soql);
                        client.sendAsync(restRequestCompanyDocumentsRecordType, new RestClient.AsyncRequestCallback() {

                            @Override
                            public void onSuccess(RestRequest request, RestResponse result) {
                                Log.d("", result.toString());
                                ArrayList<Company_Documents__c> _companyDocumentss = new ArrayList<Company_Documents__c>();

                                try {
                                    JSONObject jsonObject = new JSONObject(result.toString());
                                    JSONArray jArrayRecords = jsonObject.getJSONArray(JSONConstants.RECORDS);
                                    if (jArrayRecords.length() > 0) {
                                        for (int i = 0; i < jArrayRecords.length(); i++) {
                                            Company_Documents__c temp = new Company_Documents__c();
                                            temp.setAttachment_Id__c(jArrayRecords.getJSONObject(i).getString("Attachment_Id__c"));
                                            temp.setName(jArrayRecords.getJSONObject(i).getString("Name"));
                                            temp.setId(jArrayRecords.getJSONObject(i).getString("Id"));
                                            _companyDocumentss.add(temp);
                                        }
                                    }


                                    for (int i = 0; i < _companyDocumentss.size(); i++)
                                        for (int j = 0; j < _companyDocuments.size(); j++) {
                                            if (_companyDocuments.get(j).getName().toLowerCase().equals(_companyDocumentss.get(i).getName().toLowerCase())) {
                                                _companyDocuments.get(j).setAttachment_Id__c(_companyDocumentss.get(i).getAttachment_Id__c());
                                                _companyDocumentss.get(i).setId(_companyDocuments.get(j).getId());
                                                _companyDocuments.get(j).setHasAttachment(true);
                                            }

                                        }
                                    activity.setCompanyDocuments(_companyDocuments);
                                    lstAttachments.setAdapter(new AttachmentAdapter(getActivity().getApplicationContext(), _companyDocuments));
                                    lstAttachments.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                                        @Override
                                        public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {
//                    view.setSelected(true);
                                            new BottomSheet.Builder(getActivity()).title("Choose Attachment Option").sheet(R.menu.list).listener(new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialog, int which) {
                                                    switch (which) {
                                                        case R.id.camera:
                                                            loadImagefromCamera(position, _companyDocuments.get(position));
                                                            break;
                                                        case R.id.existing_document:
                                                            intent = new Intent(getActivity().getApplicationContext(), CompanyDocumentsActivity.class);
                                                            Gson gson = new Gson();
                                                            String companyDocumentString = gson.toJson(_companyDocuments.get(position));
                                                            intent.putExtra("position", position);
                                                            intent.putExtra("company_documents__c", companyDocumentString);
                                                            getActivity().startActivityForResult(intent, 1);
                                                            break;
                                                        case R.id.gallery:
                                                            loadImagefromGallery(position, _companyDocuments.get(position));
                                                            break;
                                                    }
                                                }
                                            }).show();
                                        }
                                    });
                                    if (_companyDocumentss.size() > 0)
                                        activity.ConnectAttachmentWithCompanyDocument(_companyDocumentss, 0);

                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }

                            }

                            @Override
                            public void onError(Exception exception) {
                                Utilities.showToast(getActivity(), RestMessages.getInstance().getErrorMessage());
                                Utilities.dismissLoadingDialog();
                                getActivity().finish();
                            }
                        });
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }
}
