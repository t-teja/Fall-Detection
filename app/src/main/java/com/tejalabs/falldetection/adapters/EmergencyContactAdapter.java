package com.tejalabs.falldetection.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.tejalabs.falldetection.R;
import com.tejalabs.falldetection.models.EmergencyContact;

import java.util.ArrayList;
import java.util.List;

public class EmergencyContactAdapter extends RecyclerView.Adapter<EmergencyContactAdapter.ContactViewHolder> {

    private List<EmergencyContact> contacts;
    private OnContactActionListener listener;

    public interface OnContactActionListener {
        void onEditContact(EmergencyContact contact);
        void onDeleteContact(EmergencyContact contact);
        void onCallContact(EmergencyContact contact);
    }

    public EmergencyContactAdapter(OnContactActionListener listener) {
        this.contacts = new ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_emergency_contact, parent, false);
        return new ContactViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ContactViewHolder holder, int position) {
        EmergencyContact contact = contacts.get(position);
        holder.bind(contact);
    }

    @Override
    public int getItemCount() {
        return contacts.size();
    }

    public void updateContacts(List<EmergencyContact> newContacts) {
        this.contacts.clear();
        this.contacts.addAll(newContacts);
        notifyDataSetChanged();
    }

    class ContactViewHolder extends RecyclerView.ViewHolder {
        private TextView tvName;
        private TextView tvPhone;
        private ImageButton btnCall;
        private ImageButton btnEdit;
        private ImageButton btnDelete;

        public ContactViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_contact_name);
            tvPhone = itemView.findViewById(R.id.tv_contact_phone);
            btnCall = itemView.findViewById(R.id.btn_call);
            btnEdit = itemView.findViewById(R.id.btn_edit);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }

        public void bind(EmergencyContact contact) {
            tvName.setText(contact.getName());
            tvPhone.setText(contact.getPhoneNumber());

            btnCall.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCallContact(contact);
                }
            });

            btnEdit.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onEditContact(contact);
                }
            });

            btnDelete.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeleteContact(contact);
                }
            });
        }
    }
}
