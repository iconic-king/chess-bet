package chessbet.adapter;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.crashlytics.android.Crashlytics;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

import chessbet.app.com.BoardActivity;
import chessbet.app.com.R;
import chessbet.domain.Constants;
import chessbet.utils.GameManager;

public class GamesAdapter extends RecyclerView.Adapter<ViewHolder>{
    private List<File> files;
    private Context context;

    public GamesAdapter(List<File> files, Context context){
        this.files = files;
        this.context = context;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.pgn_game_view, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String time = Constants.GetDigitsFromString(files.get(position).getName());
        holder.getTxtName().setText(GameManager.GAME_FILE_NAME);
        long mills = Long.parseLong(time);
        Date date = new Date(mills);
        DateFormat df = new SimpleDateFormat("dd.MM.yyyy/HH.mm.ss", Locale.US);
        holder.getTxtTime().setText(df.format(date));
        holder.getBtnDelete().setOnClickListener(v -> {
            try{
                File file = files.get(position);
                boolean delete =  file.delete();
                if(delete){
                    files.remove(file);
                    notifyItemRemoved(position);
                    Toast.makeText(context, file.getName(), Toast.LENGTH_LONG).show();
                }
            }catch (Exception ex){
                // Log any crash error when deleting
                Crashlytics.logException(ex);
            }

        });
        holder.getBtnView().setOnClickListener(v -> {
            try{
                File file = files.get(position);
                String pgn = getPGNFromFile(file);
                if(pgn != null){
                    Intent intent = new Intent(context, BoardActivity.class);
                    intent.putExtra("pgn", pgn);
                    context.startActivity(intent);
                }
            }catch (Exception ex){
                Crashlytics.logException(ex);
            }
        });
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    private String getPGNFromFile(File file){
        try {
            Scanner scanner = new Scanner(file);
            while (scanner.hasNextLine()){
                String line = scanner.nextLine();
                if(line.startsWith("1.")){
                    return line;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}

class ViewHolder extends RecyclerView.ViewHolder{
    private TextView txtName;
    private TextView txtTime;
    private Button btnView;
    private Button btnDelete;
    ViewHolder(@NonNull View itemView) {
        super(itemView);
        txtName = itemView.findViewById(R.id.txtFileName);
        txtTime = itemView.findViewById(R.id.txtTime);
        btnView = itemView.findViewById(R.id.btnView);
        btnDelete = itemView.findViewById(R.id.btnDelete);
    }

    TextView getTxtName() {
        return txtName;
    }

    TextView getTxtTime() {
        return txtTime;
    }

    Button getBtnDelete() {
        return btnDelete;
    }

    Button getBtnView() {
        return btnView;
    }
}
