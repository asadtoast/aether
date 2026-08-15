# Aether

Aether is an android native app intended to be used with a pre-existing hydrus installation.
<br>
My idea is to bring the convenience of a processed or in process hydrus gallery to handheld (android only for now...) devices.
<br><br>

<img width="360" height="732" alt="full_main_gallery" src="https://github.com/user-attachments/assets/db926c75-13ab-4c2e-89b4-f6115f2c2df0" />
<br><br>

## Media
- Supports mostly anything that can be normally played on an android device, be it your jpeg, png, gifs, mp4, webm, webp...
<br>
<img width="360" height="732" alt="fullscreen_viewer" src="https://github.com/user-attachments/assets/9b03a687-030a-454a-ad98-ec37bef84e96" />
<img width="360" height="732" alt="viewer_info_panel" src="https://github.com/user-attachments/assets/41d5a971-6a90-4e3e-ba89-d846e5946db4" />
<br><br>

- Built in hide PSD, PDF and ZIP files
- Inbox, archive and delete to selected files
- Archive / delete processing of the searched context
<br>

<img width="360" height="732" alt="archive_delete_example" src="https://github.com/user-attachments/assets/38d9cec2-c75d-45a0-872b-8a1c58217f02" />
<br><br>
  
- Simple duplicate processing
<br>

<img width="360" height="732" alt="dup_processing_a" src="https://github.com/user-attachments/assets/80f47fd5-b10a-4b26-8c2f-f3910b2b9cbd" />
<img width="360" height="732" alt="dup_processing_b" src="https://github.com/user-attachments/assets/1d4033ce-0e57-4d8c-886a-a45545595747" />
<br><br>

## Tags
- Add / remove tags to both individual or multiple selected files
- Search and manage tags
- Tag Wiki

With bigger galleries containing imports from manual, and multiple other sites, remembering what some tags meant becomes quite a difficult task so, I've added a Tag Wiki, like your normal boorus, this allows you to write a description (with support for referencing other tags from the system), check some example files from your client, add aliases that you can also search and add references to external sites.
<br>

<img width="360" height="732" alt="tagwiki" src="https://github.com/user-attachments/assets/3159c24a-8e5d-4355-8c14-649cb9942ccb" />
<img width="360" height="732" alt="tagwiki_details" src="https://github.com/user-attachments/assets/bdb3c326-b7e1-4a7d-8679-7c23e5f91f0f" />
<br><br>


## Privacy

In the same fashion as hydrus, this app is made with privacy in mind, so I have included a privacy toggle in the settings menu, to keep whatever you write private to your self and not used for training data on your keyboard maker.

The app will not store data using android's restorable structure, that means if you uninstall the app, everything gets wiped, so remember to make backups using the backup function. 
Which, speaking of, with the privacy thing in mind, I thought the best idea was to keep the app backup inside of hydrus, as a zipped file. **Bear in mind that eventhough the app contains a auto backup function, I would still strongly suggest doing ocasional manual backups.**

As far as I have tested, the app is capable of working with both local (within the same network) and remote (cloudflared/vpn) hydrus instances. 

## Getting started
For your convenience, you can find an up to date, unsigned and precompiled android apk in the releases section.

Check [here](https://github.com/asadtoast/aether/wiki) for details on how to use all the available options of the app.
