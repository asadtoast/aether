# Aether

Aether is an android native app intended to be used with a pre-existing hydrus installation.
<br>
My idea is to bring the convenience of a processed or in process hydrus gallery to handheld (android only for now...) devices.
<br><br>

<img width="360" height="732" alt="full_maingallery" src="https://github.com/user-attachments/assets/2825b38c-4ae5-411d-b337-942d2ce3572e" />
<br><br>

## Media
- Supports mostly anything that can be normally played on an android device, be it your jpeg, png, gifs, mp4, webm, webp...
- Capability to display alternate / related files
- File tag parsers
<br>

<img width="360" height="732" alt="mp4_media" src="https://github.com/user-attachments/assets/c8779b98-1e86-46d1-b793-fcde6286208a" />
<img width="360" height="732" alt="viewer_info_panel" src="https://github.com/user-attachments/assets/40cfd49d-a156-4cf8-82b8-847f25caf87e" />
<img width="360" height="732" alt="viewer_info_panel_2" src="https://github.com/user-attachments/assets/f8064e23-8540-4004-8977-6c304991d846" />
<br><br>

- Built in hide PSD, PDF and ZIP files
- Inbox, archive and delete to selected files
- Archive / delete processing of the searched context
<br>

<img width="360" height="732" alt="archive_delete_example" src="https://github.com/user-attachments/assets/6b0aa9bd-220c-4ced-842a-da054d44dd76" />
<br><br>
  
- Duplicate processing
<br>

<img width="360" height="732" alt="dup_processing_A" src="https://github.com/user-attachments/assets/cc528b46-4ed7-4b59-9e99-3990c0bd6097" />
<img width="360" height="732" alt="dup_processing_B" src="https://github.com/user-attachments/assets/cc0ead44-bf4a-43fc-b827-42e55ae5d8c8" />
<br><br>

- Group alternates processing
<br>
<img width="360" height="732" alt="group_alternate_processing_1" src="https://github.com/user-attachments/assets/9a4e3863-5c48-42c5-bbf5-1fa2c42a94ca" />
<img width="360" height="732" alt="group_alternate_processing_2" src="https://github.com/user-attachments/assets/7b62cdf2-e36e-4b74-bc99-0067756e0580" />
<img width="360" height="732" alt="group_alternate_processing_3" src="https://github.com/user-attachments/assets/3ea41aac-6fff-40c1-ae64-62d3793560bd" />
<br><br>


## Tags
- Add / remove tags to both individual or multiple selected files
- Search and manage tags
- Tag Wiki

With bigger galleries containing imports from manual, and multiple other sites, remembering what some tags meant becomes quite a difficult task so, I've added a Tag Wiki, like your normal boorus, this allows you to write a description (with support for referencing other tags from the system), check some example files from your client, add aliases that you can also search and add references to external sites.
<br>

<img width="360" height="732" alt="tagwiki" src="https://github.com/user-attachments/assets/3159c24a-8e5d-4355-8c14-649cb9942ccb" />
<img width="360" height="732" alt="tag_details" src="https://github.com/user-attachments/assets/56efffd9-06c7-4674-a1e8-1db1080b4616" />

<br><br>


## Privacy

In the same fashion as hydrus, this app is made with privacy in mind, so I have included a privacy toggle in the settings menu, to keep whatever you write private to your self and not used for training data on your keyboard maker.

The app will not store data using android's restorable structure, that means if you uninstall the app, everything gets wiped, so remember to make backups using the backup function. 
Which, speaking of, with the privacy thing in mind, I thought the best idea was to keep the app backup inside of hydrus, as a zipped file. **Bear in mind that eventhough the app contains a auto backup function, I would still strongly suggest doing ocasional manual backups.**

As far as I have tested, the app is capable of working with both local (within the same network) and remote (cloudflared/vpn) hydrus instances. 

## Getting started
For your convenience, you can find an up to date, unsigned and precompiled android apk in the releases section.

Check [here](https://github.com/asadtoast/aether/wiki) for details on how to use all the available options of the app.
